package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.selects.onTimeout
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
    private val settingRepository: SettingRepository,
) : ViewModel() {
    private val isInit = settingRepository.isInit
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun onLaunch(): Boolean {
        val init = viewModelScope.async { isInit.filterNotNull().first() }
        val canLoadList = select {
            init.onAwait { !it }
            onTimeout(timeMillis = 300L) { false }
        }
        if (!canLoadList) {
            settingRepository.putIsInit(true)
        }
        return canLoadList
    }
}
