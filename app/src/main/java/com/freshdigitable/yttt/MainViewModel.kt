package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.*
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.AccountRepository.Companion.getNewChooseAccountIntent
import com.freshdigitable.yttt.data.GoogleService
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.android.gms.common.GoogleApiAvailability
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
    private val _onAir: MutableLiveData<List<LiveVideo>> = MutableLiveData()
    val onAir: LiveData<List<LiveVideo>> = _onAir
    private val _next: MutableLiveData<List<LiveVideo>> = MutableLiveData()
    val next: LiveData<List<LiveVideo>> = _next

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

    fun onInit() {
        viewModelScope.launch { fetchLiveStreams() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun fetchLiveStreams() {
        val channelIds = liveRepository.fetchAllSubscribe().map { it.channelId }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        val videos = channelIds.asFlow()
            .map { liveRepository.fetchActivitiesList(it) }
            .map { a -> a.map { it.videoId } }
            .map { liveRepository.fetchVideoList(it) }
            .flatMapConcat { v -> flow { v.forEach { emit(it) } } } /// ???
            .filter { it.isLiveStream() }
            .shareIn(viewModelScope, SharingStarted.Eagerly)

        val onAirStream = videos.filter { it.isNowOnAir() }
            .runningFold(listOf<LiveVideo>()) { acc, a ->
                acc.toMutableList().apply { add(a) }
                    .sortedBy { it.actualStartDateTime }
            }
        viewModelScope.launch {
            onAirStream.collect {
                _onAir.postValue(it)
            }
        }

        val nextStream = videos.filter { it.isUpcoming() }
            .runningFold(listOf<LiveVideo>()) { acc, v ->
                acc.toMutableList().apply { add(v) }
                    .sortedBy { it.scheduledStartDateTime }
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
