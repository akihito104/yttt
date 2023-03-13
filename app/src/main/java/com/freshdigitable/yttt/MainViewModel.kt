package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.launch

class MainViewModel(
    private val liveRepository: YouTubeLiveRepository
) : ViewModel() {
    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YtttApp
                MainViewModel(
                    app.youTubeLiveRepository,
                )
            }
        }
    }

    val onAir: MutableLiveData<List<Video>> = MutableLiveData()
    val next: MutableLiveData<List<Video>> = MutableLiveData()

    fun login(account: String? = null): Boolean = liveRepository.login(account)

    fun createPickAccountIntent(): Intent = liveRepository.credential.newChooseAccountIntent()

    fun onInit() {
        viewModelScope.launch { fetchLiveStreams() }
    }

    private suspend fun fetchLiveStreams() {
        val channelIds = liveRepository.fetchAllSubscribe().map { it.snippet.resourceId.channelId }
        Log.d("MainActivity", "fetchSubscribeList: ${channelIds.size}")
        val activities = channelIds.flatMap { ch -> liveRepository.fetchActivitiesList(ch) }
            .filter { it.contentDetails?.upload != null }
        Log.d("MainActivity", "fetchActivitiesList: ${activities.size}")
        val videos =
            liveRepository.fetchVideoList(activities.map { it.contentDetails.upload.videoId }
                .toSet())
        Log.d("MainActivity", "fetchVideoList: ${videos.size}")
        val onAirStream = videos.filter { it.liveStreamingDetails != null }
            .filter {
                it.liveStreamingDetails.actualStartTime != null &&
                    it.liveStreamingDetails.actualEndTime == null
            }.sortedBy { it.liveStreamingDetails.actualStartTime.value }
        Log.d("MainActivity", "onAirStream: ${onAirStream.size}")
        this.onAir.postValue(onAirStream)

        val nextStream = videos.filter { it.liveStreamingDetails != null }
            .filter { it.liveStreamingDetails.actualStartTime == null }
            .sortedBy { it.liveStreamingDetails.scheduledStartTime?.value ?: -1 }
        Log.d("MainActivity", "nextStream: ${nextStream.size}")
        this.next.postValue(nextStream)
    }
}
