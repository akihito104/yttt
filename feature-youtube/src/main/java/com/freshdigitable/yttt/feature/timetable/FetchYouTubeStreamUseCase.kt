package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.AppPerformance
import com.freshdigitable.yttt.AppTrace
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscriptions
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.YouTubeDataSource.Companion.MAX_BATCH_SIZE
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
                .chunked(MAX_BATCH_SIZE).collect { ids ->
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
        return Result.success(Unit)
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
                val subs = value.onSuccess {
                    if (it is YouTubeSubscriptions.Paged) {
                        liveRepository.addSubscribes(it)
                    }
                }.onFailure { logE(throwable = it) { "fetchUploadedPlaylists: " } }
                    .getOrNull() ?: return@fold acc
                val summary = fetchSubscriptionSummary(acc, subs)
                if (summary.isEmpty()) {
                    return@fold acc.update(subs, emptyList(), null)
                }
                val tasks = summary.map {
                    coroutineScope.async(start = CoroutineStart.LAZY) {
                        val v = fetchVideoByPlaylistIdTask(it)
                        if (v.isNotEmpty()) {
                            videoUpdateTaskChannel.send(v)
                        }
                    }
                }
                trace?.incrementMetric("update_task", tasks.size.toLong())
                val job = coroutineScope.launch {
                    tasks.awaitAll()
                }
                acc.update(subs, tasks, job)
            }
        val subscriptions = playlistUpdateTaskCache.subscriptions
        if (subscriptions is YouTubeSubscriptions.Updated) {
            liveRepository.removeSubscribes(subscriptions.deleted)
        }
        playlistUpdateTaskCache.join()
    }

    private suspend fun fetchSubscriptionSummary(
        acc: PlaylistUpdateTaskCache,
        subs: YouTubeSubscriptions,
    ): List<YouTubeSubscriptionSummary> {
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
            return summary
        }
        acc.pendingSummary.addAll(needsPlaylist)
        val isSubscriptionsUpdatable = subs is YouTubeSubscriptions.Updated
        return if (acc.pendingSummary.size >= MAX_BATCH_SIZE || isSubscriptionsUpdatable) {
            val p = if (isSubscriptionsUpdatable) {
                acc.pullAllPendingSummary()
            } else {
                acc.pullPendingSummary(MAX_BATCH_SIZE)
            }
            summary - needsPlaylist.toSet() + updateSummary(p)
        } else {
            summary - needsPlaylist.toSet()
        }
    }

    private suspend fun updateSummary(summary: Collection<YouTubeSubscriptionSummary>): List<YouTubeSubscriptionSummary> {
        val s = summary.associateBy { it.channelId }
        return liveRepository.fetchChannelList(s.keys)
            .onFailure { logE(throwable = it) { "updateSummary: " } }
            .map { c ->
                c.filter { it.uploadedPlayList != null }
                    .map {
                        object : YouTubeSubscriptionSummary by checkNotNull(s[it.id]) {
                            override val uploadedPlaylistId: YouTubePlaylist.Id?
                                get() = it.uploadedPlayList
                        }
                    }
            }.getOrDefault(emptyList())
    }

    private suspend fun fetchVideoByPlaylistIdTask(summary: YouTubeSubscriptionSummary): List<YouTubeVideo.Id> {
        val id = checkNotNull(summary.uploadedPlaylistId)
        val cache = liveRepository.fetchPlaylistWithItemSummaries(id)
        val itemIds = liveRepository.fetchPlaylistWithItems(id, maxResult = 10, cache)
            .recoverCatching {
                if (cache != null && (it as? IoScope.NetworkException)?.statusCode == 404) {
                    cache.update(emptyList(), dateTimeProvider.now()).also { i ->
                        liveRepository.updatePlaylistWithItems(i)
                    }
                } else {
                    throw it
                }
            }
            .map { playlist -> checkNotNull(playlist).addedItems.map { it.videoId } }
            .onFailure { logE(throwable = it) { "fetchVideoByPlaylistIdTask: playlistId> $id" } }
            .getOrDefault(emptyList())
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
        private set

    fun update(
        subscriptions: YouTubeSubscriptions,
        tasks: List<Deferred<Unit>>,
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

    suspend fun join() {
        jobs.joinAll()
    }
}
