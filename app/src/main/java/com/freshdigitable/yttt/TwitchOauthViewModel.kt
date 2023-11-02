package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.TwitchOauthToken
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
) : ViewModel() {
    val hasTokenState: StateFlow<Boolean> = accountRepository.twitchToken
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun hasToken(): Boolean = accountRepository.getTwitchToken() != null
    suspend fun getAuthorizeUrl(): String = twitchRepository.getAuthorizeUrl()

    fun putToken(token: TwitchOauthToken) {
        viewModelScope.launch {
            accountRepository.putTwitchToken(token.accessToken)
        }
    }

    fun getMe(): LiveData<TwitchUser> = liveData {
        val user = twitchRepository.fetchMe() ?: throw IllegalStateException()
        emit(user)
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchOauthViewModel::class.simpleName
    }
}
