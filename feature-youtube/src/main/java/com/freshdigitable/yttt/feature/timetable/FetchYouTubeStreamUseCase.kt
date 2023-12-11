package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.logE
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import javax.inject.Inject

internal class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val facade: YouTubeFacade,
    private val accountRepository: AccountRepository,
    private val settingRepository: SettingRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (!accountRepository.hasAccount()) {
            return
        }
        updateStreams()
        fetchNewStreams()
        settingRepository.lastUpdateDatetime = Instant.now()
        liveRepository.cleanUp()
        facade.updateAsFreeChat()
        logD { "fetchLiveStreams: end" }
    }

    private suspend fun updateStreams() {
        val first = liveRepository.findAllUnfinishedVideos()
            .filter { it.isNowOnAir() || it.isUpcoming() }
            .map { it.id }.distinct()
        val currentVideo = facade.fetchVideoList(first).map { it.id }.toSet()
        logD { "fetchLiveStreams: currentVideo> ${currentVideo.size}" }
        val removed = first.subtract(currentVideo)
        logD { "fetchLiveStreams: removed> ${removed.size}" }
        liveRepository.removeVideo(removed)
    }

    private suspend fun fetchNewStreams() {
        val subs = liveRepository.fetchAllSubscribeSummary()
        val current = Instant.now()
        val needsUpdate =
            subs.filter { it.uploadedPlaylistId != null && it.needsUpdatePlaylist(current) }
        logD { "fetchNewStreams: subs.size> ${subs.size}" }
        val task = coroutineScope {
            needsUpdate.map { async { fetchVideoByPlaylistIdTask(it, current) } }
        }
        val ids = task.awaitAll().flatten()
        logD { "fetchNewStreams: videoId.size> ${ids.size}" }
        facade.fetchVideoList(ids)
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
                .filter { it.isArchived != true }
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
}
