package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.selects.select
import javax.inject.Inject

@Composable
fun LaunchScreen(
    viewModel: LaunchViewModel = hiltViewModel(),
    onTransition: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        val canLoadList = viewModel.onLaunch()
        onTransition(canLoadList)
    }
}

@HiltViewModel
class LaunchViewModel @Inject constructor(
    accountRepository: AccountRepository,
) : ViewModel() {
    private val hasGoogleAccount = accountRepository.googleAccount.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val hasTwitchToken: StateFlow<Boolean?> = accountRepository.twitchToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val hasAccount = combine(hasGoogleAccount, hasTwitchToken) { g, t ->
        if (g == null || t == null) null
        else g || t
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun onLaunch(): Boolean {
        val account = viewModelScope.async { hasAccount.filterNotNull().filter { it }.first() }
        val canLoadList = select {
            account.onAwait { it }
            onTimeout(timeMillis = 300L) { false }
        }
        return canLoadList
    }
}
