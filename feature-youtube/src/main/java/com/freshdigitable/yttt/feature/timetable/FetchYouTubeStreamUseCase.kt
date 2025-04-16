package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
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
        val playlistUpdateTaskCache = liveRepository.fetchPagedSubscriptionSummary()
            .fold(PlaylistUpdateTaskCache()) { acc, value ->
                val s = value.onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
                    .getOrNull() ?: return@fold acc
                acc.updateSubscriptionSummary(
                    summary = s,
                    current = dateTimeProvider.now(),
                    task = {
                        coroutineScope.async(start = CoroutineStart.LAZY) {
                            val v = fetchVideoByPlaylistIdTask(it, dateTimeProvider.now())
                            if (v.isNotEmpty()) {
                                videoUpdateTaskChannel.send(v)
                            }
                        }
                    },
                    awaitTask = {
                        coroutineScope.launch {
                            it.awaitAll()
                        }
                    },
                )
            }
        playlistUpdateTaskCache.join()
        trace?.putMetric("update_task", playlistUpdateTaskCache.tasks.size.toLong())
        trace?.putMetric("subs", playlistUpdateTaskCache.summaries.size.toLong())
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
    private var summaryCache = emptyList<YouTubeSubscriptionSummary>()
    val summaries: Set<YouTubeSubscriptionSummary> get() = summaryCache.toSet()
    private val updateTasks = mutableSetOf<Deferred<Unit>>()
    val tasks: Set<Deferred<Unit>> get() = updateTasks
    private val jobs = mutableListOf<Job>()

    fun updateSubscriptionSummary(
        summary: List<YouTubeSubscriptionSummary>,
        current: Instant,
        task: (YouTubeSubscriptionSummary) -> Deferred<Unit>,
        awaitTask: (List<Deferred<Unit>>) -> Job,
    ): PlaylistUpdateTaskCache {
        val s = summary.associateBy { it.subscriptionId }
        val c = summaryCache.map { it.subscriptionId }
        summaryCache = summary

        val newSummary = (s.keys - c.toSet()).mapNotNull { s[it] }
            .filter { it.needsUpdatePlaylist(current) }
        if (newSummary.isEmpty()) {
            return this
        }
        val updatePlaylistTask = newSummary.map(task)
        updateTasks.addAll(updatePlaylistTask)
        jobs += awaitTask(updatePlaylistTask)
        return this
    }

    suspend fun join() {
        jobs.joinAll()
    }
}
