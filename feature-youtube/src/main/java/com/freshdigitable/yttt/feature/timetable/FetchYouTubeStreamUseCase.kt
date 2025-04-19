package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.logE
import com.freshdigitable.yttt.logI
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

internal class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val accountRepository: YouTubeAccountRepository,
    private val dateTimeProvider: DateTimeProvider,
) : FetchStreamUseCase {
    private var trace: AppTrace? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke() {
        if (!accountRepository.hasAccount()) {
            return
        }
        logI { "start" }
        val t = AppPerformance.newTrace("loadList_yt")
        trace = t
        t.start()
        liveRepository.cleanUp()
        fetchAsync(
            updateCurrentVideoItemsTask = this::updateCurrentVideos,
            updateFromPlaylistTask = this::fetchUploadedPlaylists,
        ) { videoUpdateTaskChannel ->
            videoUpdateTaskChannel.consumeAsFlow()
                .flatMapConcat { it.asFlow() }
                .chunked(YouTubeDataSource.MAX_BATCH_SIZE).collect { ids ->
                    val v = liveRepository.fetchVideoList(ids.toSet<YouTubeVideo.Id>())
                        .onFailure { logE(throwable = it) { "ids: $ids" } }
                        .getOrNull() ?: return@collect

                    val archived = v.filter { it.isArchived }.map { it.id }.toSet()
                    val removed = ids - v.map { it.id }.toSet()
                    val removing = archived + removed
                    if (removing.isNotEmpty()) {
                        liveRepository.removeVideo(removing)
                        t.incrementMetric("update_remove", removing.size.toLong())
                    }

                    val url = v.filter { it.isThumbnailUpdatable }
                        .map { it.thumbnailUrl }
                    if (url.isNotEmpty()) {
                        liveRepository.removeImageByUrl(url)
                    }

                    liveRepository.addVideo(v.filter { !it.isArchived })
                    t.incrementMetric("new_stream", ids.size.toLong())
                }
        }

        liveRepository.cleanUp()
        t.stop()
        trace = null
        logI { "end" }
    }

    private suspend fun updateCurrentVideos(videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>) {
        val current = dateTimeProvider.now()
        val currentItems = liveRepository.videos.value
            .filter { it.isNowOnAir() || it.isUpcoming() || it.liveBroadcastContent == null }
            .filter { it.isUpdatable(current) }
            .map { it.id }
        videoUpdateTaskChannel.send(currentItems)
        trace?.putMetric("update_current", currentItems.size.toLong())
    }

    private suspend fun fetchUploadedPlaylists(
        coroutineScope: CoroutineScope,
        videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>,
    ) {
        val playlistUpdateTaskCache = liveRepository.fetchSubscriptions()
            .fold(PlaylistUpdateTaskCache()) { acc, value ->
                val s = value.onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
                    .getOrNull() ?: return@fold acc
                val added = if (s is YouTubeSubscriptions.Paged) {
                    s.lastPage.map { it.id }
                } else {
                    s.items.map { it.id }
                }
                trace?.incrementMetric("subs", added.size.toLong())
                val current = dateTimeProvider.now()
                val summary = liveRepository.findSubscriptionSummaries(added)
                    .filter { it.needsUpdatePlaylist(current) }
                if (summary.isEmpty()) {
                    return@fold acc.update(s, emptyList(), null)
                }
                val tasks = summary.map {
                    coroutineScope.async(start = CoroutineStart.LAZY) {
                        val v = fetchVideoByPlaylistIdTask(it, dateTimeProvider.now())
                        if (v.isNotEmpty()) {
                            videoUpdateTaskChannel.send(v)
                        }
                    }
                }
                trace?.incrementMetric("update_task", tasks.size.toLong())
                val job = coroutineScope.launch {
                    tasks.awaitAll()
                }
                acc.update(s, tasks, job)
            }
        val subscriptions = playlistUpdateTaskCache.subscriptions
        if (subscriptions is YouTubeSubscriptions.Updated) {
            liveRepository.addSubscribes(subscriptions)
            liveRepository.removeSubscribes(subscriptions.deleted)
        }
        playlistUpdateTaskCache.join()
    }

    private suspend fun fetchVideoByPlaylistIdTask(
        subscriptionSummary: YouTubeSubscriptionSummary,
        current: Instant,
    ): List<YouTubeVideo.Id> {
        val summary = if (subscriptionSummary.uploadedPlaylistId != null) {
            subscriptionSummary
        } else {
            val channel = liveRepository.fetchChannelList(setOf(subscriptionSummary.channelId))
                .onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: " } }
                .map { it.first() }.getOrNull() ?: return emptyList()
            if (channel.uploadedPlayList == null) {
                return emptyList()
            }
            object : YouTubeSubscriptionSummary by subscriptionSummary {
                override val uploadedPlaylistId: YouTubePlaylist.Id?
                    get() = channel.uploadedPlayList
            }
        }
        val id = checkNotNull(summary.uploadedPlaylistId)
        val playlistWithItems = liveRepository.fetchPlaylistWithItems(
            summary,
            current,
            maxResult = 10,
        ).onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: playlistId> $id" } }
            .getOrNull() ?: return emptyList()
        val itemIds = playlistWithItems.addedItems.map { it.videoId }
        if (itemIds.isNotEmpty()) {
            logD { "fetchVideoByPlaylistIdTask: playlistId> $id,count>${itemIds.size}" }
        }
        return itemIds
    }

    private suspend inline fun fetchAsync(
        crossinline updateCurrentVideoItemsTask: suspend (SendChannel<List<YouTubeVideo.Id>>) -> Unit,
        crossinline updateFromPlaylistTask: suspend (CoroutineScope, SendChannel<List<YouTubeVideo.Id>>) -> Unit,
        crossinline fetchVideoItemsTask: suspend (ReceiveChannel<List<YouTubeVideo.Id>>) -> Unit,
    ) = coroutineScope {
        val videoUpdateTaskChannel = Channel<List<YouTubeVideo.Id>>(Channel.BUFFERED)
        val t = launch { fetchVideoItemsTask(videoUpdateTaskChannel) }
        listOf(
            async { updateCurrentVideoItemsTask(videoUpdateTaskChannel) },
            async { updateFromPlaylistTask(this, videoUpdateTaskChannel) },
        ).awaitAll()
        videoUpdateTaskChannel.close()
        t.join()
    }
}

private class PlaylistUpdateTaskCache {
    private val updateTasks = mutableSetOf<Deferred<Unit>>()
    private val jobs = mutableListOf<Job>()
    var subscriptions: YouTubeSubscriptions? = null
    fun update(
        subscriptions: YouTubeSubscriptions,
        tasks: List<Deferred<Unit>>,
        job: Job?,
    ): PlaylistUpdateTaskCache = apply {
        this.subscriptions = subscriptions
        updateTasks.addAll(tasks)
        if (job != null) jobs.add(job)
    }

    suspend fun join() {
        jobs.joinAll()
    }
}
