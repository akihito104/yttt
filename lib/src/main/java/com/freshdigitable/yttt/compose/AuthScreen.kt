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
    onSetupCompleted: (() -> Unit)? = null,
) {
    val completeButtonEnabled = viewModel.completeButtonEnabled.collectAsState(true)
    AuthScreen(
        listItems = viewModel.getPlatformList(),
        completeButtonEnabled = { completeButtonEnabled.value },
        onSetupCompleted = if (onSetupCompleted != null) {
            {
                viewModel.onInitialSetupCompleted()
                onSetupCompleted.invoke()
            }
        } else null,
    )
}

@Composable
private fun AuthScreen(
    listItems: Collection<AccountSettingListItem>,
    completeButtonEnabled: () -> Boolean,
    onSetupCompleted: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        listItems.forEach { item ->
            item.ListBodyContent { AuthListItem(it) }
        }
        if (onSetupCompleted != null) {
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
private fun AuthListItem(
    body: AccountSettingListItem.ListBody,
) {
    ListItem(
        headlineContent = { Text(body.title) },
        trailingContent = {
            Button(
                enabled = body.enabled(),
                onClick = body.onClick,
            ) {
                Text(text = body.buttonText())
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
            completeButtonEnabled = { true },
            onSetupCompleted = {},
        )
    }
}

@LightModePreview
@Composable
fun AuthListItemPreview() {
    AppTheme {
        Column {
            AuthListItem(AccountSettingListItem.ListBody(
                title = "YouTube", enabled = { false }, buttonText = { "connected" }) {}
            )
            AuthListItem(AccountSettingListItem.ListBody(
                title = "Twitch", enabled = { true }, buttonText = { "auth" }) {}
            )
        }
    }
}
