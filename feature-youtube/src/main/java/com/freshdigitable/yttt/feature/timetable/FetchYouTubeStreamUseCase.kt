package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance.Companion.trace
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isFresh
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.isPlaylistItemUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.source.YouTubeDataSource.Companion.MAX_BATCH_SIZE
import com.freshdigitable.yttt.data.source.recoverFromNotModified
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.logE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
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
    private var trace: AppTrace? = null

    override suspend operator fun invoke(): Result<Unit> {
        if (!accountRepository.hasAccount()) {
            return Result.success(Unit)
        }
        return trace("loadList_yt") {
            trace = this
            liveRepository.cleanUp()
            fetchAsync(
                updateCurrentVideoItemsTask = this@FetchYouTubeStreamUseCase::updateCurrentVideos,
                updateFromPlaylistTask = this@FetchYouTubeStreamUseCase::fetchUploadedPlaylists,
            ) { coroutineScope, videoUpdateTaskChannel ->
                val task = videoUpdateTaskChannel.consumeAsFlow()
                    .chunkByNewVideoIdCount(MAX_BATCH_SIZE).mapAsync(coroutineScope) { batch ->
                        val videoIds = batch.map { it.videoIds }.flatten()
                        liveRepository.fetchVideoList(videoIds.toSet())
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

                                val url = v.filter { it.isThumbnailUpdatable }
                                    .map { it.thumbnailUrl }
                                if (url.isNotEmpty()) {
                                    liveRepository.removeImageByUrl(url)
                                }

                                liveRepository.updateWithVideos(
                                    archived,
                                    removed.toSet(),
                                    video.filter { !it.item.isArchived },
                                )
                                incrementMetric("new_stream", videoIds.size.toLong())
                                batch.mapNotNull { it.updatablePlaylistWithItems }.forEach {
                                    liveRepository.updatePlaylistWithItems(it.item, it.cacheControl)
                                }
                            }
                    }.toList().awaitAll()
                if (task.any { it.isFailure }) {
                    task.first { it.isFailure }.map { {} }
                } else {
                    Result.success { liveRepository.cleanUp() }
                }
            }.also { trace = null }
        }
    }

    private suspend fun updateCurrentVideos(videoUpdateTaskChannel: SendChannel<VideoUpdateBatch>) {
        val current = dateTimeProvider.now()
        val currentItems = liveRepository.fetchUpdatableVideoIds(current)
        videoUpdateTaskChannel.send(currentItems)
    }

    private suspend fun fetchUploadedPlaylists(
        coroutineScope: CoroutineScope,
        videoUpdateTaskChannel: SendChannel<VideoUpdateBatch>,
    ): Result<DeferredTask> = liveRepository.fetchAllSubscription(dateTimeProvider.now())
        .fold(PlaylistUpdateTaskCache()) { acc, value ->
            val summary = value.onFailure { return@fold acc.apply { failureResults.add(it) } }
                .getOrNull()?.item ?: throw AssertionError()
            val needsChannelDetail = summary.filter { it.uploadedPlaylistId == null }
            acc.pendingSummary.addAll(needsChannelDetail)
            val isSubscriptionTaskFinished = !(checkNotNull(value.getOrNull()).hasNextToken)
            val append =
                if (acc.pendingSummary.size >= MAX_BATCH_SIZE || isSubscriptionTaskFinished) {
                    val p = if (isSubscriptionTaskFinished) {
                        acc.pullAllPendingSummary()
                    } else {
                        acc.pullPendingSummary(MAX_BATCH_SIZE)
                    }
                    updateSummary(p).onFailure { return@fold acc.apply { failureResults.add(it) } }
                } else {
                    Result.success(emptyList())
                }

            val current = dateTimeProvider.now()
            val tasks = (summary - needsChannelDetail + append.getOrDefault(emptyList()))
                .filter { it.isPlaylistItemUpdatable(current) }
                .map { s ->
                    coroutineScope.async(start = CoroutineStart.LAZY) {
                        fetchVideoByPlaylistIdTask(s).onSuccess {
                            if (it.videoIds.isNotEmpty()) {
                                videoUpdateTaskChannel.send(it)
                            } else {
                                val playlistItems = checkNotNull(it.updatablePlaylistWithItems)
                                liveRepository.updatePlaylistWithItems(playlistItems.item, playlistItems.cacheControl)
                            }
                        }
                    }
                }
            trace?.incrementMetric("update_task", tasks.size.toLong())
            val job = coroutineScope.launch {
                tasks.awaitAll()
            }
            acc.update(summary.map { it.subscriptionId }, tasks, job, value.getOrNull()?.saveEtag)
        }.join()
        .onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
        .map {
            {
                liveRepository.syncSubscriptionList(it.ids.toSet(), it.queries)
            }
        }

    private suspend fun updateSummary(
        summary: Collection<YouTubeSubscriptionSummary>,
    ): Result<List<YouTubeSubscriptionSummary>> {
        val s = summary.associateBy { it.channelId }
        return liveRepository.fetchChannelRelatedPlaylistList(s.keys)
            .onFailure { logE(throwable = it) { "updateSummary: " } }
            .map { c ->
                c.filter { it.uploadedPlayList != null }
                    .map {
                        val base = checkNotNull(s[it.id])
                        YouTubeSubscriptionSummary.create(base, it.uploadedPlayList)
                    }
            }
    }

    private suspend fun fetchVideoByPlaylistIdTask(summary: YouTubeSubscriptionSummary): Result<VideoUpdateBatch> {
        val id = checkNotNull(summary.uploadedPlaylistId)
        return liveRepository.fetchPlaylistWithItems(id, maxResult = 10)
            .map { it.toUpdateBatch() }
            .onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: playlistId> $id" } }
            .onSuccess {
                if (it.videoIds.isNotEmpty()) {
                    logD { "fetchVideoByPlaylistIdTask: playlistId> $id,count>${it.videoIds.size}" }
                }
            }
    }

    private suspend inline fun fetchAsync(
        crossinline updateCurrentVideoItemsTask: suspend (SendChannel<VideoUpdateBatch>) -> Unit,
        crossinline updateFromPlaylistTask: suspend (
            CoroutineScope,
            SendChannel<VideoUpdateBatch>,
        ) -> Result<DeferredTask>,
        crossinline fetchVideoItemsTask: suspend (
            CoroutineScope,
            ReceiveChannel<VideoUpdateBatch>,
        ) -> Result<DeferredTask>,
    ): Result<Unit> = coroutineScope {
        val videoUpdateTaskChannel = Channel<VideoUpdateBatch>(Channel.BUFFERED)
        val fetchVideo = async { fetchVideoItemsTask(this, videoUpdateTaskChannel) }
        val tasks = listOf(
            async { Result.success(updateCurrentVideoItemsTask(videoUpdateTaskChannel)) },
            async { updateFromPlaylistTask(this, videoUpdateTaskChannel) },
        ).awaitAll()
        videoUpdateTaskChannel.close()
        val fetchVideoRes = fetchVideo.await()
        val res = tasks + fetchVideoRes
        @Suppress("UNCHECKED_CAST")
        (tasks[1] as Result<DeferredTask>).onSuccess { it() }
        fetchVideoRes.onSuccess { it() }
        if (res.any { it.isFailure }) {
            res.first { it.isFailure }
        } else {
            Result.success(Unit)
        }.map { }
    }
}
typealias DeferredTask = suspend () -> Unit

private class PlaylistUpdateTaskCache {
    private val updateTasks = mutableSetOf<Deferred<Result<*>>>()
    private val jobs = mutableListOf<Job>()
    val failureResults = mutableListOf<Throwable>()
    private val _ids = mutableListOf<YouTubeSubscription.Id>()
    val ids: List<YouTubeSubscription.Id> get() = _ids
    val queries: MutableList<YouTubeSubscriptionQuery> = mutableListOf()

    fun update(
        added: List<YouTubeSubscription.Id>,
        tasks: List<Deferred<Result<*>>>,
        job: Job?,
        query: YouTubeSubscriptionQuery? = null,
    ): PlaylistUpdateTaskCache = apply {
        _ids.addAll(added)
        updateTasks.addAll(tasks)
        if (job != null) jobs.add(job)
        if (query != null) queries.add(query)
    }

    val pendingSummary: MutableList<YouTubeSubscriptionSummary> = mutableListOf()
    fun pullPendingSummary(size: Int): List<YouTubeSubscriptionSummary> =
        pendingSummary.take(size).also {
            pendingSummary.removeAll(it)
        }

    fun pullAllPendingSummary(): List<YouTubeSubscriptionSummary> = pendingSummary

    suspend fun join(): Result<PlaylistUpdateTaskCache> {
        val taskResults = updateTasks.awaitAll()
        jobs.joinAll()
        return if (failureResults.isEmpty() && taskResults.all { it.isSuccess }) {
            Result.success(this)
        } else {
            if (failureResults.isNotEmpty()) {
                Result.failure(failureResults.first())
            } else {
                taskResults.first { it.isFailure }.map { this }
            }
        }
    }
}

private inline fun <T, R> Flow<T>.mapAsync(
    coroutineScope: CoroutineScope,
    crossinline body: suspend (T) -> R,
): Flow<Deferred<R>> = map { coroutineScope.async { body(it) } }

internal fun YouTubeSubscriptionSummary.Companion.create(
    base: YouTubeSubscriptionSummary,
    uploadedPlaylistId: YouTubePlaylist.Id?,
): YouTubeSubscriptionSummary = YouTubeSubscriptionSummaryImpl(base, uploadedPlaylistId)

internal class YouTubeSubscriptionSummaryImpl(
    private val base: YouTubeSubscriptionSummary,
    override val uploadedPlaylistId: YouTubePlaylist.Id?,
) : YouTubeSubscriptionSummary by base

private val SUBSCRIPTION_FETCH_PERIOD = Duration.ofHours(2)
internal val YouTubeRepository.subscriptionCacheControl: CacheControl
    get() = CacheControl.create(subscriptionsFetchedAt, SUBSCRIPTION_FETCH_PERIOD)

internal fun YouTubeRepository.fetchAllSubscription(
    current: Instant,
): Flow<Result<YouTubeSubscriptionSummaries>> = if (subscriptionCacheControl.isFresh(current)) {
    flow {
        var offset = 0
        do {
            val item = findSubscriptionSummariesByOffset(offset, MAX_BATCH_SIZE)
            emit(Result.success(YouTubeSubscriptionSummaries(item = item)))
            offset += MAX_BATCH_SIZE
        } while (findSubscriptionQuery(offset) != null)
    }
} else {
    flow {
        var summary = YouTubeSubscriptionSummaries(query = findSubscriptionQuery(0))
        do {
            val s = fetchPagedSubscription(summary).map {
                val o = summary.offset + MAX_BATCH_SIZE
                YouTubeSubscriptionSummaries(
                    item = findSubscriptionSummaries(it.item.map { i -> i.id }),
                    offset = o,
                    query = findSubscriptionQuery(o),
                    token = it.nextPageToken,
                    fetchedAt = it.cacheControl.fetchedAt,
                    saveEtag = YouTubeSubscriptionQuery.forAlphabetical(
                        summary.offset,
                        summary.nextPageToken,
                        it.eTag,
                    ),
                )
            }.recoverFromNotModified {
                val o = summary.offset + MAX_BATCH_SIZE
                YouTubeSubscriptionSummaries(
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

internal class YouTubeSubscriptionSummaries(
    override val offset: Int = 0,
    val item: List<YouTubeSubscriptionSummary> = emptyList(),
    private val query: YouTubeSubscriptionQuery? = null,
    private val token: String? = null,
    val fetchedAt: Instant? = null,
    val saveEtag: YouTubeSubscriptionQuery? = null,
) : YouTubeSubscriptionQuery {
    override val nextPageToken: String? get() = token ?: query?.nextPageToken
    override val eTag: String? get() = query?.eTag
    val hasNextToken: Boolean get() = (nextPageToken ?: query?.nextPageToken) != null
}

typealias VideoUpdateBatch = Pair<List<YouTubeVideo.Id>, Updatable<YouTubePlaylistWithItems>?>

val VideoUpdateBatch.videoIds: List<YouTubeVideo.Id> get() = first
val VideoUpdateBatch.updatablePlaylistWithItems: Updatable<YouTubePlaylistWithItems>? get() = second
fun Updatable<YouTubePlaylistWithItems>.toUpdateBatch(): VideoUpdateBatch = item.addedItems.map { it.videoId } to this
private suspend fun SendChannel<VideoUpdateBatch>.send(videoIds: List<YouTubeVideo.Id>) =
    videoIds.chunked(MAX_BATCH_SIZE).forEach { send(it to null) }

internal fun Flow<VideoUpdateBatch>.chunkByNewVideoIdCount(
    count: Int,
): Flow<List<VideoUpdateBatch>> {
    require(count > 0) { "size must be greater than 0" }
    return flow {
        var res: MutableList<VideoUpdateBatch>? = null
        var total = 0
        collect { pair ->
            val size = pair.first.size
            if (total + size <= count) {
                res = res?.apply { add(pair) } ?: mutableListOf(pair)
                total += size
                if (total == count) {
                    emit(res!!)
                    res = null
                    total = 0
                }
            } else {
                emit(res!!)
                res = mutableListOf(pair)
                total = pair.first.size
            }
        }
        res?.let { emit(it) }
    }
}
