package com.freshdigitable.yttt.feature.oauth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.AuthListItem
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchOauthToken

internal object TwitchAccountSettingListItem : AccountSettingListItem {
    override val platform: LivePlatform = Twitch

    @Composable
    override fun ListBodyContent() {
        ListItem()
    }

    @Composable
    private fun ListItem(
        viewModel: TwitchOauthViewModel = hiltViewModel(),
    ) {
        val hasToken = viewModel.hasTokenState.collectAsState()
        AuthListItem(
            title = "Twitch",
            enabled = { !hasToken.value },
            buttonText = { if (hasToken.value) "connected" else "auth" },
            onClick = viewModel::onLogin,
        )
    }
}

@Composable
fun TwitchAuthRedirectionDialog(
    token: TwitchOauthToken,
    onDismiss: () -> Unit,
    viewModel: TwitchOauthViewModel = hiltViewModel(),
) {
    viewModel.putToken(token)
    TwitchAuthRedirectionDialog(
        text = "Your Twitch account is connected successfully.",
        onDismiss = onDismiss,
    )
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
        confirmButton = { Text("confirm") },
    )
}

@LightModePreview
@Composable
private fun TwitchDialogPreview() {
    AppTheme {
        TwitchAuthRedirectionDialog(
            text = "Your Twitch account is connected successfully.",
            onDismiss = {},
        )
    }
}
