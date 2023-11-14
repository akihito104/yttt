package com.freshdigitable.yttt

import android.util.Log
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
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
        updateAsFreeChat()
        Log.d(TAG, "fetchLiveStreams: end")
    }

    private suspend fun updateStreams() {
        val first = liveRepository.findAllUnfinishedVideos()
            .filter { it.isNowOnAir() || it.isUpcoming() }
            .map { it.id }.distinct()
        val currentVideo = liveRepository.fetchVideoList(first).map { it.id }.toSet()
        Log.d(TAG, "fetchLiveStreams: currentVideo> ${currentVideo.size}")
        val removed = first.subtract(currentVideo)
        Log.d(TAG, "fetchLiveStreams: removed> ${removed.size}")
        liveRepository.updateVideosInvisible(removed)
    }

    private suspend fun fetchNewStreams() {
        val channelIds = liveRepository.fetchAllSubscribe(maxResult = 50).map { it.channel.id }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val channelDetails = liveRepository.fetchChannelList(channelIds)
        val task = coroutineScope {
            channelDetails.map { channelDetail -> async { fetchVideoTask(channelDetail) } }
        }
        val ids = task.awaitAll().flatten()
        liveRepository.fetchVideoList(ids)
    }

    private suspend fun fetchVideoTask(channelDetail: YouTubeChannelDetail): List<YouTubeVideo.Id> {
        val id = channelDetail.uploadedPlayList ?: return emptyList()
        try {
            val ids = liveRepository.fetchVideoIdListByPlaylistId(id)
            Log.d(TAG, "fetchLiveStreams: playlistId> $id,count>${ids.size}")
            return ids
        } catch (e: Exception) {
            if ((e as? GoogleJsonResponseException)?.statusCode == 404) {
                Log.d(TAG, "fetchLiveStreams(reload ${channelDetail.customUrl}) did not update.")
            } else {
                Log.e(TAG, "fetchLiveStreams: channel>$channelDetail", e)
            }
        }
        return emptyList()
    }

    private suspend fun updateAsFreeChat() {
        val unchecked = liveRepository.findAllUnfinishedVideos()
        val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )
        val freeChat = unchecked.filter { it.isFreeChat == null }
            .filter { v -> regex.any { v.title.contains(it) } }
            .map { it.id }
        liveRepository.addFreeChatItems(freeChat)
        val unfinished = unchecked.filter { it.isFreeChat == null }.map { it.id } - freeChat.toSet()
        liveRepository.removeFreeChatItems(unfinished)
    }

    companion object {
        @Suppress("unused")
        private val TAG = FetchYouTubeStreamUseCase::class.simpleName
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