package com.freshdigitable.yttt.feature.oauth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.LaunchAppWithUrlUseCase
import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.TwitchRepository
import com.freshdigitable.yttt.data.model.TwitchOauthToken
import com.freshdigitable.yttt.data.source.TwitchOauthStatus
import com.freshdigitable.yttt.logD
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TwitchOauthViewModel @Inject constructor(
    private val twitchRepository: TwitchRepository,
    private val accountRepository: TwitchAccountRepository,
    private val launchApp: LaunchAppWithUrlUseCase,
) : ViewModel() {
    val hasValidTokenState: StateFlow<Boolean> = combine(
        accountRepository.twitchToken.map { it != null },
        accountRepository.isTwitchTokenInvalidated.map { it ?: false },
    ) { hasToken, tokenInvalidated ->
        hasToken && !tokenInvalidated
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val oauthStatus: StateFlow<TwitchOauthStatus?> = accountRepository.twitchOauthStatus
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private suspend fun getAuthorizeUrl(state: String): String =
        accountRepository.getAuthorizeUrl(state).getOrNull() ?: ""

    fun onLogin() {
        viewModelScope.launch {
            val uuid = UUID.randomUUID().toString()
            val url = getAuthorizeUrl(uuid)
            accountRepository.putTwitchOauthState(uuid)
            accountRepository.putTwitchOauthStatus(TwitchOauthStatus.REQUESTED)
            launchApp(url) {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        }
    }

    fun clearOauthStatus() {
        viewModelScope.launch {
            accountRepository.clearTwitchOauthStatus()
        }
    }

    fun onClearAccount() {
        viewModelScope.launch {
            twitchRepository.deleteAllTables()
            accountRepository.clearTwitchToken()
            accountRepository.clearTwitchTokenInvalidated()
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchOauthViewModel::class.simpleName
    }
}

class TwitchOauthParser @Inject constructor(
    private val accountRepository: TwitchAccountRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val state = accountRepository.twitchOauthState
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    fun consumeOAuthEvent(url: String): Boolean {
        logD { "consume: $url" }
        if (!url.startsWith(BuildConfig.TWITCH_REDIRECT_URI)) {
            return false
        }
        val token = TwitchOauthToken.create(url)
        if (state.value != token.state) {
            return true // needs to inform user?
        }
        coroutineScope.launch {
            accountRepository.putTwitchToken(checkNotNull(token.accessToken))
            accountRepository.clearTwitchTokenInvalidated()
            accountRepository.clearTwitchOauthState()
            accountRepository.putTwitchOauthStatus(TwitchOauthStatus.SUCCEEDED)
        }
        return true
    }
}
