package com.freshdigitable.yttt.feature.oauth

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.TwitchOauthToken
import com.freshdigitable.yttt.data.model.TwitchUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TwitchOauthViewModel @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
    private val accountRepository: AccountRepository,
    private val launchApp: LaunchAppWithUrlUseCase,
) : ViewModel() {
    val hasTokenState: StateFlow<Boolean> = accountRepository.twitchToken
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun hasToken(): Boolean = accountRepository.getTwitchToken() != null

    fun putToken(token: TwitchOauthToken) {
        // TODO: check OAuth2 State
        viewModelScope.launch {
            accountRepository.putTwitchToken(token.accessToken)
        }
    }

    fun getMe(): LiveData<TwitchUser> = liveData {
        val user = twitchRepository.fetchMe() ?: throw IllegalStateException()
        emit(user)
    }

    private suspend fun getAuthorizeUrl(): String = twitchRepository.getAuthorizeUrl()
    fun onLogin() {
        viewModelScope.launch {
            val url = getAuthorizeUrl()
            launchApp(url) {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchOauthViewModel::class.simpleName
    }
}
