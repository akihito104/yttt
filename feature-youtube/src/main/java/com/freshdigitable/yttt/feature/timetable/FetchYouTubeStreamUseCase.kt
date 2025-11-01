package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance.Companion.trace
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isFresh
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.isPlaylistItemUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.YouTubeDataSource.Companion.MAX_BATCH_SIZE
import com.freshdigitable.yttt.data.source.recoverFromNotModified
import com.freshdigitable.yttt.logE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

internal class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchStreamUseCase {
    override suspend operator fun invoke(): Result<Unit> {
        if (!accountRepository.hasAccount()) {
            return Result.success(Unit)
        }
        return trace("loadList_yt") {
            liveRepository.cleanUp()
            val task = coroutineScope {
                merge(
                    fetchVideoUpdateBatch(this),
                    fetchUpdatableTimetableVideo(),
                ).chunkedByNewVideoIdCount(MAX_BATCH_SIZE)
                    .mapAsync(this) { res ->
                        if (res.isFailure) {
                            res.map {}
                        } else {
                            fetchVideos(res.getOrThrow())
                        }
                    }
                    .toList().awaitAll()
            }
            val throwable = task.firstOrNull { it.isFailure }
            if (throwable != null) {
                throwable.map {}
            } else {
                liveRepository.cleanUp()
                Result.success(Unit)
            }
        }
    }

    private fun fetchVideoUpdateBatch(coroutineScope: CoroutineScope): Flow<Result<VideoUpdateBatch>> =
        updateSubscription()
            .then(::fetchUpdatablePlaylistId)
            .then { createToUpdatePlaylistAndVideoTask(coroutineScope, it) }

    private fun updateSubscription(): Flow<Result<SubscriptionSummaryTask>> = flow {
        val subscriptions = liveRepository.fetchAllSubscription(dateTimeProvider.now())
            .fold(mutableListOf<Result<SubscriptionSummaryTask>>()) { acc, value ->
                acc.apply {
                    add(value)
                    emit(value)
                }
            }.toList()
        if (subscriptions.all { it.isSuccess }) {
            val s = subscriptions.map { it.getOrThrow() }
            val subIds = s.map { it.item }.flatten().map { it.subscriptionId }.toSet()
            val queries = s.mapNotNull { it.updatedQuery }
            liveRepository.syncSubscriptionList(subIds, queries)
        }
    }

    private fun fetchUpdatablePlaylistId(
        subscriptionTask: Flow<Result<SubscriptionSummaryTask>>,
    ): Flow<Result<List<YouTubePlaylist.Id>>> = flow {
        subscriptionTask.mapNotNull { res ->
            res.onFailure { emit(Result.failure(it)) }.getOrNull()
        }.fold(mutableMapOf<YouTubeChannel.Id, YouTubePlaylist.Id?>()) { acc, value ->
            val current = dateTimeProvider.now()
            val updatable = value.item.filter { it.isPlaylistItemUpdatable(current) }
                .mapNotNull { it.uploadedPlaylistId }
            if (updatable.isNotEmpty()) {
                emit(Result.success(updatable))
            }
            acc.apply {
                value.item.filter { it.uploadedPlaylistId == null }.map { it.channelId }
                    .forEach { putIfAbsent(it, null) }
                filterValues { it == null }.keys.chunked(MAX_BATCH_SIZE)
                    .filter { !value.hasNextToken || it.size == MAX_BATCH_SIZE }
                    .forEach { ch ->
                        val res = liveRepository.fetchChannelRelatedPlaylistList(ch.toSet())
                            .onFailure { logE(throwable = it) { "fetchChannelPlaylist: $ch" } }
                            .map { chPlaylist -> chPlaylist.associate { it.id to it.uploadedPlayList!! } }
                            .onSuccess { putAll(it) }
                        emit(res.map { it.values.toList() })
                    }
            }
        }
    }

    private fun createToUpdatePlaylistAndVideoTask(
        coroutineScope: CoroutineScope,
        updatePlaylistTask: Flow<Result<List<YouTubePlaylist.Id>>>,
    ): Flow<Result<VideoUpdateBatch>> = channelFlow {
        val tasks = mutableListOf<Job>()
        updatePlaylistTask.mapNotNull { res ->
            res.onFailure { send(Result.failure(it)) }.getOrNull()
        }.fold(mutableMapOf<YouTubePlaylist.Id, Job>()) { acc, value ->
            acc.values.removeIf { it.isCancelled || it.isCompleted }
            val added = value.associateWith { id ->
                coroutineScope.async(start = CoroutineStart.LAZY) {
                    val res = updatePlaylistAndVideoBatch(id)
                    if ((res.isSuccess && res.getOrNull() != null) || res.isFailure) {
                        send(res.map { checkNotNull(it) })
                    }
                }
            }
            tasks.add(coroutineScope.launch { added.values.awaitAll() })
            acc.apply { putAll(added) }
        }.values.joinAll()
        tasks.joinAll()
    }

    private suspend fun updatePlaylistAndVideoBatch(id: YouTubePlaylist.Id): Result<VideoUpdateBatch?> =
        liveRepository.fetchPlaylistWithItems(id, maxResult = 10).map {
            if (it.item.addedItems.isEmpty()) {
                liveRepository.updatePlaylistWithItems(it.item, it.cacheControl)
                null
            } else {
                it.toUpdateBatch()
            }
        }.onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: playlistId> $id" } }

    private fun fetchUpdatableTimetableVideo(): Flow<Result<VideoUpdateBatch>> = flow {
        val current = dateTimeProvider.now()
        liveRepository.fetchUpdatableVideoIds(current)
            .chunked(MAX_BATCH_SIZE)
            .forEach { emit(Result.success(it to null)) }
    }

    private suspend fun AppTrace.fetchVideos(
        batch: List<VideoUpdateBatch>,
    ): Result<List<Updatable<YouTubeVideoExtended>>> {
        val videoIds = batch.map { it.videoIds }.flatten()
        return liveRepository.fetchVideoList(videoIds.toSet())
            .onFailure { logE(throwable = it) { "ids: $videoIds" } }
            .onSuccess { video ->
                val v = video.map { it.item }
                val archived = v.filter { it.isArchived }.map { it.id }.toSet()
                if (archived.isNotEmpty()) {
                    incrementMetric("update_remove", archived.size.toLong())
                }
                val removed = videoIds - v.map { it.id }.toSet()
                if (removed.isNotEmpty()) {
                    incrementMetric("update_remove", removed.size.toLong())
                }

                val url = v.filter { it.isThumbnailUpdatable || it.isArchived }
                    .map { it.thumbnailUrl }
                if (url.isNotEmpty()) {
                    liveRepository.removeImageByUrl(url)
                }

                liveRepository.updateWithVideos(videoIds, video)
                incrementMetric("new_stream", videoIds.size.toLong())
                batch.mapNotNull { it.updatablePlaylistWithItems }.forEach {
                    liveRepository.updatePlaylistWithItems(it.item, it.cacheControl)
                }
            }
    }

    companion object {
        private inline fun <T, R> Flow<T>.mapAsync(
            coroutineScope: CoroutineScope,
            crossinline body: suspend (T) -> R,
        ): Flow<Deferred<R>> = map { coroutineScope.async { body(it) } }

        private inline fun <T, R> Flow<T>.then(crossinline block: (Flow<T>) -> Flow<R>): Flow<R> = block(this)
    }
}

private val SUBSCRIPTION_FETCH_PERIOD = Duration.ofHours(2)
internal val YouTubeRepository.subscriptionCacheControl: CacheControl
    get() = CacheControl.create(subscriptionsFetchedAt, SUBSCRIPTION_FETCH_PERIOD)

internal fun YouTubeRepository.fetchAllSubscription(
    current: Instant,
): Flow<Result<SubscriptionSummaryTask>> = if (subscriptionCacheControl.isFresh(current)) {
    flow {
        var offset = 0
        do {
            val item = findSubscriptionSummariesByOffset(offset, MAX_BATCH_SIZE)
            emit(Result.success(SubscriptionSummaryTask(item = item)))
            offset += MAX_BATCH_SIZE
        } while (findSubscriptionQuery(offset) != null)
    }
} else {
    flow {
        var summary = SubscriptionSummaryTask(query = findSubscriptionQuery(0))
        do {
            val s = fetchPagedSubscription(summary).map {
                val o = summary.offset + MAX_BATCH_SIZE
                SubscriptionSummaryTask(
                    item = findSubscriptionSummaries(it.item.map { i -> i.id }),
                    offset = o,
                    query = findSubscriptionQuery(o),
                    token = it.nextPageToken,
                    fetchedAt = it.cacheControl.fetchedAt,
                    updatedQuery = YouTubeSubscriptionQuery.forAlphabetical(
                        summary.offset,
                        summary.nextPageToken,
                        it.eTag,
                    ),
                )
            }.recoverFromNotModified {
                val o = summary.offset + MAX_BATCH_SIZE
                SubscriptionSummaryTask(
                    item = findSubscriptionSummariesByOffset(summary.offset, MAX_BATCH_SIZE),
                    offset = o,
                    query = findSubscriptionQuery(o),
                    fetchedAt = it.fetchedAt,
                )
            }.onFailure {
                emit(Result.failure(it))
                return@flow
            }
            emit(s)
            summary = s.getOrThrow()
        } while (summary.nextPageToken != null)
        if (summary.fetchedAt != null) {
            subscriptionsFetchedAt = summary.fetchedAt
        }
    }
}

internal class SubscriptionSummaryTask(
    override val offset: Int = 0,
    val item: List<YouTubeSubscriptionSummary> = emptyList(),
    private val query: YouTubeSubscriptionQuery? = null,
    private val token: String? = null,
    val fetchedAt: Instant? = null,
    val updatedQuery: YouTubeSubscriptionQuery? = null,
) : YouTubeSubscriptionQuery {
    override val nextPageToken: String? get() = token ?: query?.nextPageToken
    override val eTag: String? get() = query?.eTag
    val hasNextToken: Boolean get() = nextPageToken != null
}

typealias VideoUpdateBatch = Pair<List<YouTubeVideo.Id>, Updatable<YouTubePlaylistWithItems>?>

val VideoUpdateBatch.videoIds: List<YouTubeVideo.Id> get() = first
val VideoUpdateBatch.updatablePlaylistWithItems: Updatable<YouTubePlaylistWithItems>? get() = second
fun Updatable<YouTubePlaylistWithItems>.toUpdateBatch(): VideoUpdateBatch = item.addedItems.map { it.videoId } to this

internal fun Flow<Result<VideoUpdateBatch>>.chunkedByNewVideoIdCount(
    count: Int,
): Flow<Result<List<VideoUpdateBatch>>> {
    require(count > 0) { "size must be greater than 0" }
    return flow {
        var batches: MutableList<VideoUpdateBatch>? = null
        var total = 0
        collect { res ->
            val batch = res.onFailure { emit(Result.failure(it)) }.getOrNull() ?: return@collect
            val size = batch.videoIds.size
            if (total + size <= count) {
                batches = batches?.apply { add(batch) } ?: mutableListOf(batch)
                total += size
                if (total == count) {
                    emit(Result.success(batches!!))
                    batches = null
                    total = 0
                }
            } else {
                emit(Result.success(batches!!))
                batches = mutableListOf(batch)
                total = batch.videoIds.size
            }
        }
        batches?.let { emit(Result.success(it)) }
    }
}
