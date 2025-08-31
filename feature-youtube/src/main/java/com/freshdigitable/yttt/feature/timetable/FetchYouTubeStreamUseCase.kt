package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance.Companion.trace
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isFresh
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubePlaylist
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapConcat
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
        trace("loadList_yt") {
            trace = this
            liveRepository.cleanUp()
            fetchAsync(
                updateCurrentVideoItemsTask = this@FetchYouTubeStreamUseCase::updateCurrentVideos,
                updateFromPlaylistTask = this@FetchYouTubeStreamUseCase::fetchUploadedPlaylists,
            ) { coroutineScope, videoUpdateTaskChannel ->
                val task = videoUpdateTaskChannel.consumeAsFlow()
                    .flatMapConcat { it.asFlow() }
                    .chunked(MAX_BATCH_SIZE).mapAsync(coroutineScope) { ids ->
                        liveRepository.fetchVideoList(ids.toSet())
                            .onFailure { logE(throwable = it) { "ids: $ids" } }
                            .onSuccess { video ->
                                val v = video.map { it.item }
                                val archived = v.filter { it.isArchived }.map { it.id }.toSet()
                                val removed = ids - v.map { it.id }.toSet()
                                val removing = archived + removed
                                if (removing.isNotEmpty()) {
                                    liveRepository.removeVideo(removing)
                                    incrementMetric("update_remove", removing.size.toLong())
                                }

                                val url = v.filter { it.isThumbnailUpdatable }
                                    .map { it.thumbnailUrl }
                                if (url.isNotEmpty()) {
                                    liveRepository.removeImageByUrl(url)
                                }

                                liveRepository.addVideo(video.filter { !it.item.isArchived })
                                incrementMetric("new_stream", ids.size.toLong())
                            }
                    }.toList().awaitAll()
                if (task.any { it.isFailure }) {
                    task.first { it.isFailure }.map { }
                } else {
                    Result.success(Unit)
                }
            }.onFailure { return Result.failure(it) }

            liveRepository.cleanUp()
            trace = null
        }
        return Result.success(Unit)
    }

    private suspend fun updateCurrentVideos(videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>) {
        val current = dateTimeProvider.now()
        val currentItems = liveRepository.fetchUpdatableVideoIds(current)
        videoUpdateTaskChannel.send(currentItems)
    }

    private suspend fun fetchUploadedPlaylists(
        coroutineScope: CoroutineScope,
        videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>,
    ): Result<Unit> = liveRepository.fetchAllSubscription(dateTimeProvider.now())
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
                            if (it.isNotEmpty()) {
                                videoUpdateTaskChannel.send(it)
                            }
                        }
                    }
                }
            trace?.incrementMetric("update_task", tasks.size.toLong())
            val job = coroutineScope.launch {
                tasks.awaitAll()
            }
            acc.update(summary.map { it.subscriptionId }, tasks, job)
        }.join()
        .onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
        .onSuccess {
            liveRepository.cleanUpByRemainingSubscriptionIds(it.ids.toSet())
        }.map { }

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

    private suspend fun fetchVideoByPlaylistIdTask(summary: YouTubeSubscriptionSummary): Result<List<YouTubeVideo.Id>> {
        val id = checkNotNull(summary.uploadedPlaylistId)
        return liveRepository.fetchPlaylistWithItems(id, maxResult = 10)
            .map { u -> u?.item?.addedItems?.map { it.videoId } ?: emptyList() }
            .onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: playlistId> $id" } }
            .onSuccess {
                if (it.isNotEmpty()) {
                    logD { "fetchVideoByPlaylistIdTask: playlistId> $id,count>${it.size}" }
                }
            }
    }

    private suspend inline fun fetchAsync(
        crossinline updateCurrentVideoItemsTask: suspend (SendChannel<List<YouTubeVideo.Id>>) -> Unit,
        crossinline updateFromPlaylistTask: suspend (CoroutineScope, SendChannel<List<YouTubeVideo.Id>>) -> Result<Unit>,
        crossinline fetchVideoItemsTask: suspend (CoroutineScope, ReceiveChannel<List<YouTubeVideo.Id>>) -> Result<Unit>,
    ): Result<Unit> = coroutineScope {
        val videoUpdateTaskChannel = Channel<List<YouTubeVideo.Id>>(Channel.BUFFERED)
        val t = async { fetchVideoItemsTask(this, videoUpdateTaskChannel) }
        val tasks = listOf(
            async { Result.success(updateCurrentVideoItemsTask(videoUpdateTaskChannel)) },
            async { updateFromPlaylistTask(this, videoUpdateTaskChannel) },
        ).awaitAll()
        videoUpdateTaskChannel.close()
        val res = tasks + t.await()
        if (res.any { it.isFailure }) {
            res.first { it.isFailure }
        } else {
            Result.success(Unit)
        }
    }
}

private class PlaylistUpdateTaskCache {
    private val updateTasks = mutableSetOf<Deferred<Result<*>>>()
    private val jobs = mutableListOf<Job>()
    val failureResults = mutableListOf<Throwable>()
    private val _ids = mutableListOf<YouTubeSubscription.Id>()
    val ids: List<YouTubeSubscription.Id> get() = _ids

    fun update(
        added: List<YouTubeSubscription.Id>,
        tasks: List<Deferred<Result<*>>>,
        job: Job?,
    ): PlaylistUpdateTaskCache = apply {
        _ids.addAll(added)
        updateTasks.addAll(tasks)
        if (job != null) jobs.add(job)
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
            val s = fetchPagedSubscription(summary).onSuccess {
                addSubscriptionEtag(summary.offset, summary.nextPageToken, checkNotNull(it.eTag))
            }.map {
                val o = summary.offset + MAX_BATCH_SIZE
                YouTubeSubscriptionSummaries(
                    item = findSubscriptionSummaries(it.item.map { i -> i.id }),
                    offset = o,
                    query = findSubscriptionQuery(o),
                    _token = it.nextPageToken,
                    fetchedAt = it.cacheControl.fetchedAt,
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
    private val _token: String? = null,
    val fetchedAt: Instant? = null,
) : YouTubeSubscriptionQuery {
    override val nextPageToken: String? get() = _token ?: query?.nextPageToken
    override val eTag: String? get() = query?.eTag
    val hasNextToken: Boolean get() = (nextPageToken ?: query?.nextPageToken) != null
}
