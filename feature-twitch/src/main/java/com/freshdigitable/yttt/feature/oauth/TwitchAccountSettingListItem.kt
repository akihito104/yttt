package com.freshdigitable.yttt.feature.oauth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.Twitch

internal object TwitchAccountSettingListItem : AccountSettingListItem {
    override val platform: LivePlatform = Twitch

    @Composable
    override fun ListBodyContent(listItem: @Composable (AccountSettingListItem.ListBody) -> Unit) {
        ListItem(listItem = listItem)
    }

    @Composable
    private fun ListItem(
        listItem: @Composable (AccountSettingListItem.ListBody) -> Unit,
        viewModel: TwitchOauthViewModel = hiltViewModel(),
    ) {
        val hasToken = viewModel.hasTokenState.collectAsState()
        val oauthStatus = viewModel.oauthStatus.collectAsState()
        if (oauthStatus.value == TwitchOauthStatus.SUCCEEDED) {
            TwitchAuthRedirectionDialog(
                text = "Your Twitch account is linked successfully.",
                onDismiss = viewModel::clearOauthStatus,
            )
        }
        listItem(
            AccountSettingListItem.ListBody(
                title = "Twitch",
                enabled = { !hasToken.value },
                buttonText = { if (hasToken.value) "linked" else "auth" },
                onClick = viewModel::onLogin,
                onUnlink = viewModel::onClearAccount,
            )
        )
    }
}

@Composable
private fun TwitchAuthRedirectionDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text("Twitch Authentication") },
        text = { Text(text = text) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("confirm")
            }
        },
    )
}

@LightModePreview
@Composable
private fun TwitchDialogPreview() {
    AppTheme {
        TwitchAuthRedirectionDialog(
            text = "Your Twitch account is linked successfully.",
            onDismiss = {},
        )
    }
}
