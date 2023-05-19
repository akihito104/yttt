package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.AccountRepository.Companion.getNewChooseAccountIntent
import com.freshdigitable.yttt.data.GoogleService
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchLiveRepository
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val accountRepository: AccountRepository,
    private val googleService: GoogleService,
    private val twitchRepository: TwitchLiveRepository,
) : ViewModel() {

    fun getConnectionStatus(): Int = googleService.getConnectionStatusCode()

    fun isUserResolvableError(statusCode: Int): Boolean =
        googleService.isUserResolvableError(statusCode)

    val googleApiAvailability: GoogleApiAvailability get() = googleService.googleApiAvailability

    fun hasAccount(): Boolean = accountRepository.hasAccount()

    fun login(account: String? = null): Boolean {
        if (account != null) {
            accountRepository.putAccount(account)
        }
        val accountName = accountRepository.getAccount()
        if (accountName != null) {
            accountRepository.setSelectedAccountName(accountName)
            return true
        }
        return false
    }

    fun createPickAccountIntent(): Intent = accountRepository.getNewChooseAccountIntent()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

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

    private val videos = liveRepository.videos.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val onAir: LiveData<List<LiveVideo>> = combine(videos, twitchRepository.onAir) { yt, tw ->
        yt + tw
    }.map { v -> v.filter { it.isNowOnAir() }.sortedByDescending { it.actualStartDateTime } }
        .asLiveData(viewModelScope.coroutineContext)
    val upcoming: LiveData<List<LiveVideo>> = combine(videos, twitchRepository.upcoming) { yt, tw ->
        yt + tw
    }.map { v -> v.filter { it.isUpcoming() }.sortedBy { it.scheduledStartDateTime } }
        .asLiveData(viewModelScope.coroutineContext)
    val tabs: LiveData<List<TabData>> = combine(
        TimetablePage.values().map { p ->
            p.bind(this).map { it.size }.distinctUntilChanged().map { TabData(p, it) }.asFlow()
        }
    ) { tabs -> tabs.apply { sortBy { it.index } }.toList() }
        .asLiveData(viewModelScope.coroutineContext)

    private suspend fun fetchLiveStreams() {
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
