package com.freshdigitable.yttt

import android.util.Log
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary.Companion.needsUpdatePlaylist
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import javax.inject.Inject

interface FetchStreamUseCase {
    suspend operator fun invoke()
}

class FetchYouTubeStreamUseCase @Inject constructor(
    private val liveRepository: YouTubeRepository,
    private val facade: YouTubeFacade,
    private val accountRepository: AccountRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (!accountRepository.hasAccount()) {
            return
        }
        updateStreams()
        fetchNewStreams()
        liveRepository.lastUpdateDatetime = Instant.now()
        liveRepository.cleanUp()
        facade.updateAsFreeChat()
        Log.d(TAG, "fetchLiveStreams: end")
    }

    private suspend fun updateStreams() {
        val first = liveRepository.findAllUnfinishedVideos()
            .filter { it.isNowOnAir() || it.isUpcoming() }
            .map { it.id }.distinct()
        val currentVideo = facade.fetchVideoList(first).map { it.id }.toSet()
        Log.d(TAG, "fetchLiveStreams: currentVideo> ${currentVideo.size}")
        val removed = first.subtract(currentVideo)
        Log.d(TAG, "fetchLiveStreams: removed> ${removed.size}")
        liveRepository.removeVideo(removed)
    }

    private suspend fun fetchNewStreams() {
        val subs = liveRepository.fetchAllSubscribeSummary()
        val current = Instant.now()
        val needsUpdate =
            subs.filter { it.uploadedPlaylistId != null && it.needsUpdatePlaylist(current) }
        Log.d(TAG, "fetchNewStreams: subs.size> ${subs.size}")
        val task = coroutineScope {
            needsUpdate.map { async { fetchVideoByPlaylistIdTask(it, current) } }
        }
        val ids = task.awaitAll().flatten()
        Log.d(TAG, "fetchNewStreams: videoId.size> ${ids.size}")
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
                Log.d(TAG, "fetchVideoByPlaylistIdTask: playlistId> $id,count>${itemIds.size}")
            }
            return itemIds
        } catch (e: Exception) {
            if ((e as? GoogleJsonResponseException)?.statusCode == 404) {
                Log.d(TAG, "fetchVideoByPlaylistIdTask(reload $id): no items found.")
            } else {
                Log.e(TAG, "fetchVideoByPlaylistIdTask: playlist>$id", e)
            }
        }
        return emptyList()
    }

    companion object {
        @Suppress("unused")
        private val TAG = FetchYouTubeStreamUseCase::class.simpleName
    }
}

class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
        val videos = repository.fetchVideoList(ids)
        val unchecked = videos.filter { it.isFreeChat == null }
            .associateWith { isFreeChat(it) }
        val add = unchecked.entries.filter { (_, f) -> f }.map { it.key.id }
        val remove = unchecked.map { it.key.id } - add.toSet()
        if (add.isNotEmpty()) {
            repository.addFreeChatItems(add)
        }
        if (remove.isNotEmpty()) {
            repository.removeFreeChatItems(remove)
        }
        return repository.fetchVideoList(ids)
    }

    private fun isFreeChat(video: YouTubeVideo): Boolean {
        return regex.any { video.title.contains(it) }
    }

    suspend fun updateAsFreeChat() {
        val unchecked = repository.findAllUnfinishedVideos()
        val freeChat = unchecked.filter { it.isFreeChat == null }
            .filter { v -> regex.any { v.title.contains(it) } }
            .map { it.id }
        repository.addFreeChatItems(freeChat)
        val unfinished = unchecked.filter { it.isFreeChat == null }.map { it.id } - freeChat.toSet()
        repository.removeFreeChatItems(unfinished)
    }

    companion object {
        private val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )
    }
}

class FetchTwitchStreamUseCase @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
) : FetchStreamUseCase {
    override suspend operator fun invoke() {
        if (accountRepository.getTwitchToken() == null) {
            return
        }
        val me = twitchRepository.fetchMe() ?: return
        val streams = twitchRepository.fetchFollowedStreams()
        val following = twitchRepository.fetchAllFollowings(me.id)
        val tasks = coroutineScope {
            following.map { async { twitchRepository.fetchFollowedStreamSchedule(it.id) } }
        }
        val schedules = tasks.awaitAll()
        val users = streams.map { it.user.id } + schedules.flatten().map { it.broadcaster.id }
        twitchRepository.findUsersById(users)
    }
}
