package com.freshdigitable.yttt.feature.oauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSettingViewModel @Inject constructor(
    private val listItem: ClassMap<LivePlatform, AccountSettingListItem>,
    private val settingRepository: SettingRepository,
) : ViewModel() {
    val completeButtonEnabled: StateFlow<Boolean> = flowOf(true) // TODO
        .stateIn(viewModelScope, SharingStarted.Lazily, true)
    val completeButtonVisible: StateFlow<Boolean> = settingRepository.isInit
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun getPlatformList(): List<AccountSettingListItem> {
        return listItem.values.sortedBy { it.platform.name }
    }

    fun onInitialSetupCompleted() {
        viewModelScope.launch {
            settingRepository.putIsInit(false)
        }
    }
}
