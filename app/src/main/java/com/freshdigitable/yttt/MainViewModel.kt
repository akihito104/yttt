package com.freshdigitable.yttt

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.source.TwitchLiveRepository
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    val canUpdate: Boolean
        get() {
            val lastUpdateDatetime = liveRepository.lastUpdateDatetime ?: return true
            return (lastUpdateDatetime + Duration.ofMinutes(30)) <= Instant.now()
        }

    fun loadList() {
        viewModelScope.launch {
            if (_isLoading.value == false) {
                _isLoading.postValue(true)
                listOf(
                    async { fetchLiveStreams() },
                    async { fetchTwitchStream() },
                ).awaitAll()
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun fetchLiveStreams() {
        if (!accountRepository.hasAccount()) {
            return
        }
        val first = liveRepository.findAllUnfinishedVideos()
            .filter { it.isNowOnAir() || it.isUpcoming() }
            .map { it.id }.distinct()
        val currentVideo = liveRepository.fetchVideoList(first).map { it.id }.toSet()
        Log.d(TAG, "fetchLiveStreams: currentVideo> ${currentVideo.size}")
        val removed = first.subtract(currentVideo)
        Log.d(TAG, "fetchLiveStreams: removed> ${removed.size}")
        liveRepository.deleteVideo(removed)

        val channelIds = liveRepository.fetchAllSubscribe().map { it.channel.id }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val channelDetails = liveRepository.fetchChannelList(channelIds)
        val task = channelDetails.map { channelDetail ->
            viewModelScope.async { fetchVideoTask(channelDetail) }
        }
        task.awaitAll()
        liveRepository.lastUpdateDatetime = Instant.now()
        Log.d(TAG, "fetchLiveStreams: end")
    }

    private suspend fun fetchVideoTask(channelDetail: LiveChannelDetail) {
        val id = channelDetail.uploadedPlayList ?: return
        try {
            val videos = liveRepository.fetchVideoListByPlaylistId(id)
            Log.d(TAG, "fetchLiveStreams: playlistId> $id,count>${videos.size}")
        } catch (e: Exception) {
            if ((e as? GoogleJsonResponseException)?.statusCode == 404) {
                Log.d(TAG, "fetchLiveStreams(reload ${channelDetail.customUrl}) did not update.")
            } else {
                Log.e(TAG, "fetchLiveStreams: channel>$channelDetail", e)
            }
        }
    }

    private suspend fun fetchTwitchStream() {
        if (accountRepository.getTwitchToken() == null) {
            return
        }
        twitchRepository.fetchFollowedStreams()
        val me = twitchRepository.fetchMe() ?: return
        val following = twitchRepository.fetchAllFollowings(me.id)
        following.map {
            viewModelScope.async { twitchRepository.fetchFollowedStreamSchedule(it.channel.id) }
        }.awaitAll()
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }
}

@HiltViewModel
class OnAirListViewModel @Inject constructor(
    liveRepository: YouTubeLiveRepository,
    twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    val items: StateFlow<List<LiveVideo>> =
        combine(liveRepository.videos, twitchRepository.onAir) { yt, tw -> yt + tw }
            .map { v ->
                v.filter { it.isNowOnAir() }
                    .sortedByDescending { it.actualStartDateTime }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val tabData: StateFlow<TabData> = items.map { it.size }
        .distinctUntilChanged()
        .map { TabData(TimetablePage.OnAir, it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, TabData(TimetablePage.OnAir, 0))
}

@HiltViewModel
class UpcomingListViewModel @Inject constructor(
    liveRepository: YouTubeLiveRepository,
    twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    val items: StateFlow<Map<String, List<LiveVideo>>> =
        combine(liveRepository.videos, twitchRepository.upcoming) { yt, tw -> yt + tw }
            .map { v ->
                v.filter { it.isUpcoming() }
                    .sortedBy { it.scheduledStartDateTime }
                    .groupBy {
                        it.scheduledStartDateTime?.toLocalDateTime()?.truncatedTo(ChronoUnit.DAYS)
                            ?.format(dateWeekdayFormatter) ?: ""
                    }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    val tabData: StateFlow<TabData> = items.map { items ->
        items.values.map { it.size }.fold(0) { acc, i -> acc + i }
    }
        .distinctUntilChanged()
        .map { TabData(TimetablePage.Upcoming, it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, TabData(TimetablePage.Upcoming, 0))
}
