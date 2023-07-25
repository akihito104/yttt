package com.freshdigitable.yttt.compose

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.source.local.AndroidPreferencesDataStore
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val text = viewModel.changeDateTime.collectAsState()
    AppSettingsScreen(
        text = { text.value },
        onClicked = { viewModel.onClick(it) },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppSettingsScreen(
    text: () -> String,
    onClicked: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var isExpanded by remember { mutableStateOf(false) }
        ListItem(
            text = { Text("time to change the date") },
            trailing = { Text(text()) },
            modifier = Modifier.clickable {
                isExpanded = true
            },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            AppSettingsViewModel.changeDateTimeList.forEach {
                DropdownMenuItem(
                    onClick = {
                        onClicked(it)
                        isExpanded = false
                    },
                ) {
                    Text(text = "$it:00")
                }
            }
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun AppSettingsScreenPreview() {
    MdcTheme {
        AppSettingsScreen(text = { "24:00" }, onClicked = {})
    }
}

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
