package com.freshdigitable.yttt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: SettingRepository,
) : ViewModel() {
    val changeDateTime: StateFlow<String> = repository.changeDateTime
        .map { it ?: 24 }
        .map { "$it:00" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "24:00")

    fun onClick(value: Int) {
        viewModelScope.launch {
            repository.putTimeToChangeDate(value)
        }
    }

    companion object {
        val changeDateTimeList: IntRange = 24..27
    }
}
