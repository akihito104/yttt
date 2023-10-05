package com.freshdigitable.yttt

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.compose.TimetableMenuItem
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
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
        liveRepository.updateVideosInvisible(removed)

        val channelIds = liveRepository.fetchAllSubscribe(maxResult = 50).map { it.channel.id }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val channelDetails = liveRepository.fetchChannelList(channelIds)
        val task = channelDetails.map { channelDetail ->
            viewModelScope.async { fetchVideoTask(channelDetail) }
        }
        val ids = task.awaitAll().flatten()
        liveRepository.fetchVideoList(ids)
        liveRepository.lastUpdateDatetime = Instant.now()
        liveRepository.cleanUp()
        updateAsFreeChat()
        Log.d(TAG, "fetchLiveStreams: end")
    }

    private suspend fun fetchVideoTask(channelDetail: LiveChannelDetail): List<LiveVideo.Id> {
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

    private val _selectedItem: MutableStateFlow<LiveVideo?> = MutableStateFlow(null)
    val menuItems: StateFlow<List<TimetableMenuItem>> = _selectedItem.map {
        if (it == null) emptyList()
        else {
            listOfNotNull(
                if (it.isFreeChat == true) TimetableMenuItem.REMOVE_FREE_CHAT else TimetableMenuItem.ADD_FREE_CHAT,
                if (it.id.platform == LivePlatform.TWITCH && !it.isNowOnAir()) null else TimetableMenuItem.LAUNCH_LIVE,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onMenuClicked(id: LiveVideo.Id) {
        viewModelScope.launch {
            val v = when (id.platform) {
                LivePlatform.YOUTUBE -> liveRepository.fetchVideoDetail(id)
                LivePlatform.TWITCH -> twitchRepository.fetchStreamDetail(id)
            }
            _selectedItem.value = v
        }
    }

    fun onMenuClosed() {
        _selectedItem.value = null
    }

    fun onMenuItemClicked(item: TimetableMenuItem, appLauncher: (Intent) -> Unit) {
        val video = checkNotNull(_selectedItem.value)
        val id = video.id
        when (item) {
            TimetableMenuItem.ADD_FREE_CHAT -> {
                if (id.platform == LivePlatform.YOUTUBE) {
                    checkAsFreeChat(id)
                }
            }

            TimetableMenuItem.REMOVE_FREE_CHAT -> {
                if (id.platform == LivePlatform.YOUTUBE) {
                    uncheckAsFreeChat(id)
                }
            }

            TimetableMenuItem.LAUNCH_LIVE -> {
                val url = when (id.platform) {
                    LivePlatform.YOUTUBE -> "https://youtube.com/watch?v=${id.value}"
                    LivePlatform.TWITCH -> "https://twitch.tv/${(video.channel as LiveChannelDetail).customUrl}"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                appLauncher(intent)
            }
        }
    }

    private fun checkAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.addFreeChatItems(listOf(id))
        }
    }

    private fun uncheckAsFreeChat(id: LiveVideo.Id) {
        viewModelScope.launch {
            liveRepository.removeFreeChatItems(listOf(id))
        }
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
    prefs: AndroidPreferencesDataStore,
) : ViewModel() {
    private val upcomingItems =
        combine(liveRepository.videos, twitchRepository.upcoming) { yt, tw ->
            val week = Instant.now().plus(Duration.ofDays(7L))
            (yt + tw.filter { it.scheduledStartDateTime?.isBefore(week) == true })
                .filter { it.isUpcoming() && it.isFreeChat != true }
                .sortedBy { it.scheduledStartDateTime }
        }
    private val extraHourOfDay = prefs.changeDateTime.map {
        Duration.ofHours(((it ?: 24) - 24).toLong())
    }
    val items: StateFlow<Map<String, List<LiveVideo>>> =
        combine(upcomingItems, extraHourOfDay) { v, t ->
            v.groupBy {
                (checkNotNull(it.scheduledStartDateTime) - t)
                    .toLocalDateTime()
                    .truncatedTo(ChronoUnit.DAYS)
                    .format(dateWeekdayFormatter)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    val tabData: StateFlow<TabData> = items.map { items ->
        items.values.map { it.size }.fold(0) { acc, i -> acc + i }
    }
        .distinctUntilChanged()
        .map { TabData(TimetablePage.Upcoming, it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, TabData(TimetablePage.Upcoming, 0))

    val freeChat: StateFlow<List<LiveVideo>> = liveRepository.videos
        .map { v -> v.filter { it.isFreeChat == true }.sortedBy { it.channel.id.value } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val freeChatTab: StateFlow<TabData> = freeChat.map { it.size }
        .distinctUntilChanged()
        .map { TabData(TimetablePage.FreeChat, it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, TabData(TimetablePage.FreeChat, 0))
}
