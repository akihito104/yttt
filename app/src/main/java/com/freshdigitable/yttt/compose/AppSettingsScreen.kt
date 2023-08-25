package com.freshdigitable.yttt.compose

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.AppSettingsViewModel

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

@Composable
private fun AppSettingsScreen(
    text: () -> String,
    onClicked: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var isExpanded by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("time to change the date") },
            trailingContent = {
                Column {
                    Text(text())
                    DropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false },
                    ) {
                        AppSettingsViewModel.changeDateTimeList.forEach {
                            DropdownMenuItem(
                                text = { Text(text = "$it:00") },
                                onClick = {
                                    onClicked(it)
                                    isExpanded = false
                                },
                            )
                        }
                    }
                }
            },
            modifier = Modifier.clickable {
                isExpanded = true
            },
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun AppSettingsScreenPreview() {
    AppTheme {
        AppSettingsScreen(text = { "24:00" }, onClicked = {})
    }
}
