package com.freshdigitable.yttt.feature.oauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.GoogleService
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeOauthViewModel @Inject constructor(
    private val accountRepository: YouTubeAccountRepository,
    private val googleService: GoogleService,
    private val newChooseAccountIntentProvider: NewChooseAccountIntentProvider,
) : ViewModel() {
    val googleServiceState: StateFlow<AuthState?> = combine(
        googleService.connectionStatus,
        accountRepository.googleAccount,
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
    }

    fun createPickAccountIntent(): NewChooseAccountIntentProvider = newChooseAccountIntentProvider

    fun clearAccount() {
        viewModelScope.launch {
            accountRepository.clearAccount()
        }
    }

    sealed class AuthState {
        object Succeeded : AuthState()
        object HasNoAccount : AuthState()
        class ServiceConnectionRecoverable(val code: Int) : AuthState()
        object ServiceConnectionFailed : AuthState()
    }
}
