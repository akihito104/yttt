package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

internal class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val facade: YouTubeFacade,
    private val accountRepository: YouTubeAccountRepository,
    private val settingRepository: SettingRepository,
    private val dateTimeProvider: DateTimeProvider,
    private val coroutineScope: CoroutineScope,
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

        fetchAsync(
            updateCurrentVideoItemsTask = this::updateCurrentVideos,
            updateFromPlaylistTask = this::fetchUploadedPlaylists,
        ) { videoUpdateTaskChannel ->
            val idCache = mutableSetOf<YouTubeVideo.Id>()
            videoUpdateTaskChannel.consumeAsFlow()
                .flatMapConcat { it.asFlow() }
                .filter { id -> !idCache.contains(id).also { idCache.add(id) } }
                .chunked(50).collect { ids ->
                    val v = facade.fetchVideoList(ids.toSet())
                    val removed = v.filter { it.isArchived }.map { it.id }.toSet()
                    if (removed.isNotEmpty()) {
                        liveRepository.removeVideo(removed)
                        t.incrementMetric("update_remove", removed.size.toLong())
                    }

                    val old = liveRepository.videos.value.associateBy { it.id }
                    val url = v.filter {
                        val o = old[it.id] ?: return@filter false
                        o.title != it.title || (o.isUpcoming() && it.isNowOnAir())
                    }.map { it.thumbnailUrl }
                    if (url.isNotEmpty()) {
                        liveRepository.removeImageByUrl(url)
                    }

                    liveRepository.addVideo(v)
                    t.incrementMetric("new_stream", ids.size.toLong())
                }
        }

        settingRepository.lastUpdateDatetime = dateTimeProvider.now()
        liveRepository.cleanUp()
        t.stop()
        trace = null
        logI { "end" }
    }

    private suspend fun updateCurrentVideos(videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>) {
        val current = dateTimeProvider.now()
        val currentItems = liveRepository.videos.value
            .filter { it.isNowOnAir() || it.isUpcoming() || it.liveBroadcastContent == null }
            .filter { it.needsUpdate(current) }
            .map { it.id }
        videoUpdateTaskChannel.send(currentItems)
        trace?.putMetric("update_current", currentItems.size.toLong())
    }

    private suspend fun fetchUploadedPlaylists(videoUpdateTaskChannel: SendChannel<List<YouTubeVideo.Id>>) {
        val playlistUpdateTaskCache = liveRepository.fetchPagedSubscriptionSummary()
            .fold(PlaylistUpdateTaskCache()) { acc, value ->
                val v = value.associateBy { it.subscriptionId }
                val newSummary = (v.keys - acc.summaries).mapNotNull { v[it] }
                if (newSummary.isEmpty()) {
                    return@fold acc
                }
                val current = dateTimeProvider.now()
                val tasks = newSummary.associate { s ->
                    val t = if (s.isPlaylistUpdatable(current)) {
                        coroutineScope.async(start = CoroutineStart.LAZY) {
                            fetchVideoByPlaylistIdTask(s, dateTimeProvider.now())
                        }
                    } else {
                        null
                    }
                    s.subscriptionId to t
                }
                val t = tasks.values.mapNotNull { it }
                if (t.isEmpty()) {
                    return@fold acc.apply { addTasks(tasks, null) }
                }
                val job = coroutineScope.launch {
                    val ids = t.awaitAll()
                        .filter { it.isNotEmpty() }
                        .flatten()
                    videoUpdateTaskChannel.send(ids)
                }
                acc.apply { addTasks(tasks, job) }
            }
        playlistUpdateTaskCache.join()
        trace?.putMetric("update_task", playlistUpdateTaskCache.tasks.size.toLong())
        trace?.putMetric("subs", playlistUpdateTaskCache.summaries.size.toLong())
    }

    private suspend fun fetchVideoByPlaylistIdTask(
        subscriptionSummary: YouTubeSubscriptionSummary,
        current: Instant
    ): List<YouTubeVideo.Id> {
        val id = checkNotNull(subscriptionSummary.uploadedPlaylistId)
        try {
            val itemIds = liveRepository.fetchPlaylistItemSummaries(
                subscriptionSummary,
                current,
                maxResult = 10,
            )
                .filter { it.isArchived != true && it.isExpired(current) }
                .map { it.videoId }
            if (itemIds.isNotEmpty()) {
                logD { "fetchVideoByPlaylistIdTask: playlistId> $id,count>${itemIds.size}" }
            }
            return itemIds
        } catch (e: Exception) {
            logE(throwable = e) { "fetchVideoByPlaylistIdTask: playlist>$id" }
        }
        return emptyList()
    }

    private suspend fun fetchAsync(
        updateCurrentVideoItemsTask: suspend (SendChannel<List<YouTubeVideo.Id>>) -> Unit,
        updateFromPlaylistTask: suspend (SendChannel<List<YouTubeVideo.Id>>) -> Unit,
        fetchVideoItemsTask: suspend (ReceiveChannel<List<YouTubeVideo.Id>>) -> Unit,
    ) {
        val videoUpdateTaskChannel = Channel<List<YouTubeVideo.Id>>(Channel.BUFFERED)
        val t = coroutineScope.launch { fetchVideoItemsTask(videoUpdateTaskChannel) }
        listOf(
            coroutineScope.async { updateCurrentVideoItemsTask(videoUpdateTaskChannel) },
            coroutineScope.async { updateFromPlaylistTask(videoUpdateTaskChannel) },
        ).awaitAll()
        videoUpdateTaskChannel.close()
        t.join()
    }

    companion object {
        private fun YouTubeSubscriptionSummary.isPlaylistUpdatable(current: Instant): Boolean =
            uploadedPlaylistId != null && needsUpdatePlaylist(current)

        private fun YouTubePlaylistItemSummary.isExpired(current: Instant): Boolean =
            (videoExpiredAt ?: Instant.EPOCH) <= current
    }
}

private class PlaylistUpdateTaskCache {
    private val cache = mutableMapOf<YouTubeSubscription.Id, Deferred<List<YouTubeVideo.Id>>?>()
    private val jobs = mutableListOf<Job>()
    val summaries: Set<YouTubeSubscription.Id> get() = cache.keys.toSet()
    val tasks: Set<Deferred<List<YouTubeVideo.Id>>> get() = cache.values.mapNotNull { it }.toSet()
    fun addTasks(
        tasks: Map<YouTubeSubscription.Id, Deferred<List<YouTubeVideo.Id>>?>,
        job: Job?,
    ) {
        this.cache += tasks
        if (job != null) this.jobs.add(job)
    }

    suspend fun join() {
        jobs.joinAll()
    }
}
