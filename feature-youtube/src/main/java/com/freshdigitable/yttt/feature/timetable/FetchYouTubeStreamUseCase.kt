package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance.Companion.trace
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource.Companion.MAX_BATCH_SIZE
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.logE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchStreamUseCase {
    private var trace: AppTrace? = null

    @OptIn(ExperimentalCoroutinesApi::class)
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
            ) { videoUpdateTaskChannel ->
                val task = videoUpdateTaskChannel.consumeAsFlow()
                    .flatMapConcat { it.asFlow() }
                    .chunked(MAX_BATCH_SIZE).map { ids ->
                        liveRepository.fetchVideoList(ids.toSet())
                            .onFailure { logE(throwable = it) { "ids: $ids" } }
                            .onSuccess { v ->
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

                                liveRepository.addVideo(v.filter { !it.isArchived })
                                incrementMetric("new_stream", ids.size.toLong())
                            }
                    }.toList()
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
        val currentItems = liveRepository.videos.value
            .filter { it.isNowOnAir() || it.isUpcoming() || it.liveBroadcastContent == null }
            .filter { it.isUpdatable(current) }
            .map { it.id }
        videoUpdateTaskChannel.send(currentItems)
    }

    private suspend fun fetchUploadedPlaylists(
        coroutineScope: CoroutineScope,
        videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>,
    ): Result<Unit> = liveRepository.fetchSubscriptions()
        .fold(PlaylistUpdateTaskCache()) { acc, value ->
            val subs = value.onFailure { return@fold acc.apply { failureResults.add(it) } }
                .onSuccess {
                    if (it is YouTubeSubscriptions.Paged) {
                        liveRepository.addSubscribes(it)
                    }
                }
                .getOrNull() ?: throw AssertionError()
            val summaryRes = fetchSubscriptionSummary(acc, subs)
                .onFailure { return@fold acc.apply { failureResults.add(it) } }
                .onSuccess {
                    if (it.isEmpty()) {
                        return@fold acc.update(subs, emptyList(), null)
                    }
                }
            val summary = summaryRes.getOrNull() ?: throw AssertionError()
            val tasks = summary.map {
                coroutineScope.async(start = CoroutineStart.LAZY) {
                    fetchVideoByPlaylistIdTask(it).onSuccess {
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
            acc.update(subs, tasks, job)
        }.join()
        .onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
        .onSuccess {
            val subscriptions = it.subscriptions
            if (subscriptions is YouTubeSubscriptions.Updated) {
                liveRepository.removeSubscribes(subscriptions.deleted)
            }
        }.map { }

    private suspend fun fetchSubscriptionSummary(
        taskCache: PlaylistUpdateTaskCache,
        subs: YouTubeSubscriptions,
    ): Result<List<YouTubeSubscriptionSummary>> {
        val added = if (subs is YouTubeSubscriptions.Paged) {
            subs.lastPage.map { it.id }
        } else {
            subs.items.map { it.id }
        }
        trace?.incrementMetric("subs", added.size.toLong())

        val current = dateTimeProvider.now()
        val summary = liveRepository.findSubscriptionSummaries(added)
            .filter { it.needsUpdatePlaylist(current) }
        val needsPlaylist = summary.filter { it.uploadedPlaylistId == null }
        if (needsPlaylist.isEmpty()) {
            return Result.success(summary)
        }
        taskCache.pendingSummary.addAll(needsPlaylist)
        val isSubscriptionsUpdatable = subs is YouTubeSubscriptions.Updated
        return if (taskCache.pendingSummary.size >= MAX_BATCH_SIZE || isSubscriptionsUpdatable) {
            val p = if (isSubscriptionsUpdatable) {
                taskCache.pullAllPendingSummary()
            } else {
                taskCache.pullPendingSummary(MAX_BATCH_SIZE)
            }
            updateSummary(p).map { summary - needsPlaylist.toSet() + it }
        } else {
            Result.success(summary - needsPlaylist.toSet())
        }
    }

    private suspend fun updateSummary(
        summary: Collection<YouTubeSubscriptionSummary>,
    ): Result<List<YouTubeSubscriptionSummary>> {
        val s = summary.associateBy { it.channelId }
        return liveRepository.fetchChannelList(s.keys)
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
        val cache = liveRepository.fetchPlaylistWithItemSummaries(id)
        return liveRepository.fetchPlaylistWithItems(id, maxResult = 10, cache)
            .recoverCatching {
                if (cache != null && (it as? NetworkResponse.Exception)?.statusCode == 404) {
                    cache.update(emptyList(), dateTimeProvider.now()).also { i ->
                        liveRepository.updatePlaylistWithItems(i)
                    }
                } else {
                    throw it
                }
            }
            .map { playlist -> checkNotNull(playlist).addedItems.map { it.videoId } }
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
        crossinline fetchVideoItemsTask: suspend (ReceiveChannel<List<YouTubeVideo.Id>>) -> Result<Unit>,
    ): Result<Unit> = coroutineScope {
        val videoUpdateTaskChannel = Channel<List<YouTubeVideo.Id>>(Channel.BUFFERED)
        val t = async { fetchVideoItemsTask(videoUpdateTaskChannel) }
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
    var subscriptions: YouTubeSubscriptions? = null
        private set
    val failureResults = mutableListOf<Throwable>()

    fun update(
        subscriptions: YouTubeSubscriptions,
        tasks: List<Deferred<Result<*>>>,
        job: Job?,
    ): PlaylistUpdateTaskCache = apply {
        this.subscriptions = subscriptions
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

internal fun YouTubeSubscriptionSummary.Companion.create(
    base: YouTubeSubscriptionSummary,
    uploadedPlaylistId: YouTubePlaylist.Id?
): YouTubeSubscriptionSummary = YouTubeSubscriptionSummaryImpl(base, uploadedPlaylistId)

internal class YouTubeSubscriptionSummaryImpl(
    private val base: YouTubeSubscriptionSummary,
    override val uploadedPlaylistId: YouTubePlaylist.Id?
) : YouTubeSubscriptionSummary by base
