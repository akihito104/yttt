package com.freshdigitable.yttt.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.freshdigitable.yttt.compose.preview.PreviewLightMode
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.YouTube
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
        } else {
            null
        },
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
            item.ListBodyContent { AuthListItem(it, item.platform) }
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
    platform: LivePlatform,
) {
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .background(Color(platform.color))
                    .size(24.dp),
            )
        },
        headlineContent = { Text(body.title) },
        trailingContent = {
            Row {
                var expanded by remember { mutableStateOf(false) }
                var isShowDialog by remember { mutableStateOf(false) }
                Button(
                    enabled = body.enabled(),
                    onClick = body.onClick,
                ) {
                    Text(text = body.buttonText())
                }
                IconButton(
                    enabled = !body.enabled(),
                    onClick = { expanded = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "",
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Unlink") },
                        onClick = {
                            isShowDialog = true
                            expanded = false
                        },
                    )
                }
                if (isShowDialog) {
                    DisconnectConfirmingDialog(
                        platform = platform,
                        onConfirmClicked = {
                            body.onUnlink()
                            isShowDialog = false
                        },
                        onDismiss = { isShowDialog = false },
                    )
                }
            }
        },
    )
}

@Composable
fun DisconnectConfirmingDialog(
    platform: LivePlatform,
    onConfirmClicked: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = "Unlink account")
        },
        text = {
            Text(
                text = "This app will unlink your ${platform.name} account. " +
                    "This operation cannot be undone.",
            )
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("cancel")
            }
        },
        confirmButton = {
            Button(onClick = onConfirmClicked) {
                Text(text = "unlink")
            }
        },
        onDismissRequest = onDismiss,
    )
}

@PreviewLightMode
@Composable
private fun AuthListItemPreview() {
    AppTheme {
        Column {
            AuthListItem(
                AccountSettingListItem.ListBody(
                    title = "YouTube",
                    enabled = { false },
                    buttonText = { "linked" },
                    onUnlink = {},
                    onClick = {},
                ),
                YouTube,
            )
            AuthListItem(
                AccountSettingListItem.ListBody(
                    title = "Twitch",
                    enabled = { true },
                    buttonText = { "auth" },
                    onUnlink = {},
                    onClick = {},
                ),
                Twitch,
            )
        }
    }
}

@PreviewLightMode
@Composable
private fun DisconnectConfirmingDialogPreview() {
    AppTheme {
        DisconnectConfirmingDialog(
            platform = YouTube,
            onConfirmClicked = {},
        ) {}
    }
}

@PreviewLightMode
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
