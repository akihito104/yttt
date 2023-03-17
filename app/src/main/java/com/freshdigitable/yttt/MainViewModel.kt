package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.*
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.GoogleService
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.services.youtube.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val accountRepository: AccountRepository,
    private val googleService: GoogleService,
) : ViewModel() {
    private val _onAir: MutableLiveData<List<Video>> = MutableLiveData()
    val onAir: LiveData<List<Video>> = _onAir
    private val _next: MutableLiveData<List<Video>> = MutableLiveData()
    val next: LiveData<List<Video>> = _next

    fun getConnectionStatus(): Int = googleService.getConnectionStatusCode()

    fun isUserResolvableError(statusCode: Int): Boolean =
        googleService.isUserResolvableError(statusCode)

    val googleApiAvailability: GoogleApiAvailability get() = googleService.googleApiAvailability

    fun hasAccount(): Boolean = accountRepository.getAccount() != null

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

    @OptIn(FlowPreview::class)
    private suspend fun fetchLiveStreams() {
        val channelIds = liveRepository.fetchAllSubscribe().map { it.snippet.resourceId.channelId }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val videos = channelIds.asFlow()
            .map { liveRepository.fetchActivitiesList(it) }
            .map { a ->
                a.filter { it.contentDetails?.upload != null }
                    .map { it.contentDetails.upload.videoId }
            }
            .map { liveRepository.fetchVideoList(it) }
            .flatMapConcat { v -> flow { v.forEach { emit(it) } } } /// ???
            .filter { it.liveStreamingDetails != null }
            .shareIn(viewModelScope, SharingStarted.Eagerly)

        val onAirStream = videos.filter {
            it.liveStreamingDetails.actualStartTime != null &&
                it.liveStreamingDetails.actualEndTime == null
        }.runningFold(listOf<Video>()) { acc, a ->
            acc.toMutableList().apply { add(a) }
                .sortedBy { it.liveStreamingDetails.actualStartTime.value }
        }
        viewModelScope.launch {
            onAirStream.collect {
                _onAir.postValue(it)
            }
        }

        val nextStream = videos.filter { it.liveStreamingDetails.actualStartTime == null }
            .runningFold(listOf<Video>()) { acc, v ->
                acc.toMutableList().apply { add(v) }
                    .sortedBy {
                        it.liveStreamingDetails.scheduledStartTime?.value ?: Long.MAX_VALUE
                    }
            }
        viewModelScope.launch {
            nextStream.collect {
                _next.postValue(it)
            }
        }
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }
}
