package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.services.youtube.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _onAir: MutableLiveData<List<Video>> = MutableLiveData()
    val onAir: LiveData<List<Video>> = _onAir
    private val _next: MutableLiveData<List<Video>> = MutableLiveData()
    val next: LiveData<List<Video>> = _next

    fun login(account: String? = null): Boolean {
        if (account != null) {
            accountRepository.putAccount(account)
        }
        val accountName = accountRepository.getAccount()
        if (accountName != null) {
            liveRepository.credential.selectedAccountName = accountName
            return true
        }
        return false
    }

    fun createPickAccountIntent(): Intent = liveRepository.credential.newChooseAccountIntent()

    fun onInit() {
        viewModelScope.launch { fetchLiveStreams() }
    }

    private suspend fun fetchLiveStreams() {
        val channelIds = liveRepository.fetchAllSubscribe().map { it.snippet.resourceId.channelId }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val activities = channelIds.flatMap { ch -> liveRepository.fetchActivitiesList(ch) }
            .filter { it.contentDetails?.upload != null }
        Log.d(TAG, "fetchActivitiesList: ${activities.size}")
        val videos =
            liveRepository.fetchVideoList(activities.map { it.contentDetails.upload.videoId }
                .toSet())
        Log.d(TAG, "fetchVideoList: ${videos.size}")
        val onAirStream = videos.filter { it.liveStreamingDetails != null }
            .filter {
                it.liveStreamingDetails.actualStartTime != null &&
                    it.liveStreamingDetails.actualEndTime == null
            }.sortedBy { it.liveStreamingDetails.actualStartTime.value }
        Log.d(TAG, "onAirStream: ${onAirStream.size}")
        _onAir.postValue(onAirStream)

        val nextStream = videos.filter { it.liveStreamingDetails != null }
            .filter { it.liveStreamingDetails.actualStartTime == null }
            .sortedBy { it.liveStreamingDetails.scheduledStartTime?.value ?: -1 }
        Log.d(TAG, "nextStream: ${nextStream.size}")
        _next.postValue(nextStream)
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }
}
