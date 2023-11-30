package com.freshdigitable.yttt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val dataStore: AndroidPreferencesDataStore,
) : ViewModel() {
    val changeDateTime: StateFlow<String> = dataStore.changeDateTime
        .map { it ?: 24 }
        .map { "$it:00" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "24:00")

    fun onClick(value: Int) {
        viewModelScope.launch {
            dataStore.putTimeToChangeDate(value)
        }
    }

    companion object {
        val changeDateTimeList: IntRange = 24..27
    }
}
