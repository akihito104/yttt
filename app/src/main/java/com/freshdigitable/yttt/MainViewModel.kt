package com.freshdigitable.yttt

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.AccountRepository.Companion.getNewChooseAccountIntent
import com.freshdigitable.yttt.data.GoogleService
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val liveRepository: YouTubeLiveRepository,
    private val accountRepository: AccountRepository,
    private val googleService: GoogleService,
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

    fun onInit() {
        viewModelScope.launch { fetchLiveStreams() }
    }

    private val videos = liveRepository.videos
    val onAir: LiveData<List<LiveVideo>> = videos.map { v ->
        Log.d(TAG, "onair.runningFold: ${v.size}")
        v.filter { it.isNowOnAir() }.sortedByDescending { it.actualStartDateTime }
    }
        .asLiveData(viewModelScope.coroutineContext)
    val next: LiveData<List<LiveVideo>> = videos.map { v ->
        Log.d(TAG, "upcoming.runningFold: ${v.size}")
        v.filter { it.isUpcoming() }.sortedBy { it.scheduledStartDateTime }
    }
        .asLiveData(viewModelScope.coroutineContext)

    private suspend fun fetchLiveStreams() {
        val channelIds = liveRepository.fetchAllSubscribe().map { it.channelId }
        Log.d(TAG, "fetchSubscribeList: ${channelIds.size}")
        channelIds.forEach { c ->
            val logs = liveRepository.fetchLiveChannelLogs(c)
            liveRepository.fetchVideoList(logs.map { it.videoId })
        }
    }

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }
}
