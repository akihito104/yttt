package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.source.TwitchLiveRepository
import com.freshdigitable.yttt.data.source.TwitchOauthToken
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TwitchOauthViewModel @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    fun hasToken(): Boolean = accountRepository.getTwitchToken() != null
    suspend fun getAuthorizeUrl(): String = twitchRepository.getAuthorizeUrl()

    fun putToken(token: TwitchOauthToken) {
        accountRepository.putTwitchToken(token.accessToken)
    }

    fun getMe(): LiveData<LiveChannelDetail> = liveData {
        val user = twitchRepository.fetchMe() ?: throw IllegalStateException()
        emit(user)
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchOauthViewModel::class.simpleName
    }
}
