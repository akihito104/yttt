package com.freshdigitable.yttt.feature.oauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSettingViewModel @Inject constructor(
    private val listItem: Set<@JvmSuppressWildcards AccountSettingListItem>,
    private val settingRepository: SettingRepository,
) : ViewModel() {
    val completeButtonEnabled: StateFlow<Boolean> = flowOf(true) // TODO
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun getPlatformList(): List<AccountSettingListItem> = listItem.sortedBy { it.platform.name }

    fun onInitialSetupCompleted() {
        viewModelScope.launch {
            settingRepository.putIsInit(false)
        }
    }
}
