package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.feature.oauth.AccountSettingListItem
import com.freshdigitable.yttt.feature.oauth.AccountSettingViewModel

@Composable
fun AuthScreen(
    viewModel: AccountSettingViewModel = hiltViewModel(),
    onSetupCompleted: () -> Unit,
) {
    val completeButtonEnabled = viewModel.completeButtonEnabled.collectAsState(true)
    val completeButtonVisible = viewModel.completeButtonVisible.collectAsState(false)
    AuthScreen(
        listItems = viewModel.getPlatformList(),
        completeButtonEnabled = { completeButtonEnabled.value },
        completeButtonVisible = { completeButtonVisible.value },
        onSetupCompleted = {
            viewModel.onInitialSetupCompleted()
            onSetupCompleted()
        },
    )
}

@Composable
private fun AuthScreen(
    listItems: Collection<AccountSettingListItem>,
    completeButtonVisible: () -> Boolean,
    completeButtonEnabled: () -> Boolean,
    onSetupCompleted: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        listItems.forEach {
            it.ListBodyContent()
        }
        if (completeButtonVisible()) {
            Button(
                enabled = completeButtonEnabled(),
                onClick = onSetupCompleted,
            ) {
                Text("complete setup")
            }
        }
    }
}

@Composable
fun AuthListItem(
    title: String,
    enabled: () -> Boolean,
    buttonText: @Composable () -> String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Button(
                enabled = enabled(),
                onClick = onClick,
            ) {
                Text(text = buttonText())
            }
        },
    )
}

@LightModePreview
@Composable
private fun AuthScreenPreview() {
    AppTheme {
        AuthScreen(
            listItems = emptyList(),
            completeButtonVisible = { true },
            completeButtonEnabled = { true },
            onSetupCompleted = {},
        )
    }
}
