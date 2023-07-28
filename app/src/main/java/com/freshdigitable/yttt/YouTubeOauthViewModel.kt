package com.freshdigitable.yttt

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.AccountRepository.Companion.getNewChooseAccountIntent
import com.freshdigitable.yttt.data.GoogleService
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeOauthViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val googleService: GoogleService,
) : ViewModel() {
    private val googleAccount = accountRepository.googleAccount
    val hasAccount = accountRepository.googleAccount.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val googleServiceState: StateFlow<AuthState?> = combine(
        googleService.connectionStatus,
        googleAccount,
    ) { state, account ->
        if (state == null) {
            googleService.getConnectionStatus()
            return@combine null
        }
        if (state is GoogleService.ConnectionStatus.Failure) {
            if (state.isUserRecoverable) {
                AuthState.ServiceConnectionRecoverable(state.code)
            } else {
                AuthState.ServiceConnectionFailed
            }
        } else if (account == null) {
            AuthState.HasNoAccount
        } else {
            AuthState.Succeeded
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val googleApiAvailability: GoogleApiAvailability get() = googleService.googleApiAvailability

    fun hasAccount(): Boolean = accountRepository.hasAccount()

    fun login(account: String? = null) {
        if (account != null) {
            viewModelScope.launch {
                accountRepository.putAccount(account)
            }
        }
        val a = checkNotNull(accountRepository.getAccount() ?: account) {
            "login: accountName is not set."
        }
        accountRepository.setSelectedAccountName(a)
    }

    fun createPickAccountIntent(): Intent = accountRepository.getNewChooseAccountIntent()

    sealed class AuthState {
        object Succeeded : AuthState()
        object HasNoAccount : AuthState()
        class ServiceConnectionRecoverable(val code: Int) : AuthState()
        object ServiceConnectionFailed : AuthState()
    }
}
