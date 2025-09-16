package com.freshdigitable.yttt.feature.oauth

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.YouTube
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.common.GoogleApiAvailability

internal object YouTubeAccountSettingListItem : AccountSettingListItem {
    override val platform: LivePlatform = YouTube

    @Composable
    override fun ListBodyContent(listItem: @Composable (AccountSettingListItem.ListBody) -> Unit) {
        ListItem(listItem = listItem)
    }

    @Composable
    private fun ListItem(
        listItem: @Composable (AccountSettingListItem.ListBody) -> Unit,
        viewModel: YouTubeOauthViewModel = hiltViewModel(),
    ) {
        val googleServiceState = viewModel.googleServiceState.collectAsState(initial = null)
        YouTubeListItem(
            holder = rememberYouTubeAuthStateHolder(
                pickAccountIntentProvider = viewModel.createPickAccountIntent(),
                googleApiAvailabilityProvider = { viewModel.googleApiAvailability },
                login = { viewModel.login(it) },
                hasGoogleAccount = { viewModel.hasAccount() },
            ),
            googleServiceStateProvider = { googleServiceState.value },
            listItem = listItem,
            onUnlinkClicked = viewModel::clearAccount
        )
    }
}

@Composable
private fun YouTubeListItem(
    holder: YouTubeAuthStateHolder,
    googleServiceStateProvider: () -> YouTubeOauthViewModel.AuthState?,
    listItem: @Composable (AccountSettingListItem.ListBody) -> Unit,
    onUnlinkClicked: () -> Unit,
) {
    val googleServiceState = googleServiceStateProvider()
    if (googleServiceState is YouTubeOauthViewModel.AuthState.ServiceConnectionRecoverable) {
        val activity = LocalContext.current as Activity
        holder.showDialog(activity, googleServiceState.code)
    }
    val hasNoAccount = googleServiceState is YouTubeOauthViewModel.AuthState.HasNoAccount
    listItem(
        AccountSettingListItem.ListBody(
            title = "YouTube",
            enabled = { hasNoAccount },
            buttonText = { googleServiceState.stateText() },
            onUnlink = onUnlinkClicked,
            onClick = {
                if (hasNoAccount) {
                    holder.launchPermissionRequestOrPickAccount()
                }
            },
        )
    )
}

@Composable
private fun YouTubeOauthViewModel.AuthState?.stateText(): String {
    return when (this) {
        YouTubeOauthViewModel.AuthState.HasNoAccount -> "auth"
        YouTubeOauthViewModel.AuthState.ServiceConnectionFailed -> "service denied"
        is YouTubeOauthViewModel.AuthState.ServiceConnectionRecoverable -> ""
        YouTubeOauthViewModel.AuthState.Succeeded -> "linked"
        else -> "auth"
    }
}

private fun pickAccountContract(
    createIntent: NewChooseAccountIntentProvider,
): ActivityResultContract<Unit, String> = object : ActivityResultContract<Unit, String>() {
    override fun createIntent(context: Context, input: Unit): Intent = createIntent()

    override fun parseResult(resultCode: Int, intent: Intent?): String {
        if (resultCode == AppCompatActivity.RESULT_OK && intent != null && intent.extras != null) {
            return intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: ""
        }
        return ""
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberYouTubeAuthStateHolder(
    pickAccountIntentProvider: NewChooseAccountIntentProvider,
    login: (String?) -> Unit,
    googleApiAvailabilityProvider: () -> GoogleApiAvailability,
    hasGoogleAccount: () -> Boolean,
): YouTubeAuthStateHolder {
    if (hasGoogleAccount()) {
        return YouTubeAuthStateHolder(googleApiAvailability = googleApiAvailabilityProvider())
    }
    val accountPicker = rememberLauncherForActivityResult(
        contract = pickAccountContract(pickAccountIntentProvider),
    ) {
        if (it.isNotEmpty()) {
            login(it)
        }
    }
    val permissionState = rememberPermissionState(permission = Manifest.permission.GET_ACCOUNTS) {
        if (it) accountPicker.launch(Unit)
    }
    val dialogState = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            if (!hasGoogleAccount()) {
                YouTubeAuthStateHolder
                    .launchPermissionRequestOrPickAccount(permissionState, accountPicker)
            } else {
                login(null)
            }
        }
    }
    val youTubeAuthStateHolder = remember {
        YouTubeAuthStateHolder(
            accountPicker = accountPicker,
            permissionState = permissionState,
            dialogState = dialogState,
            googleApiAvailability = googleApiAvailabilityProvider(),
        )
    }
    return youTubeAuthStateHolder
}

@OptIn(ExperimentalPermissionsApi::class)
private class YouTubeAuthStateHolder(
    private val accountPicker: ManagedActivityResultLauncher<Unit, String>? = null,
    private val permissionState: PermissionState? = null,
    private val dialogState: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null,
    private val googleApiAvailability: GoogleApiAvailability,
) {
    fun showDialog(activity: Activity, code: Int) {
        checkNotNull(dialogState)
        googleApiAvailability.showErrorDialogFragment(activity, code, dialogState, null)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    fun launchPermissionRequestOrPickAccount() {
        checkNotNull(permissionState)
        checkNotNull(accountPicker)
        Companion.launchPermissionRequestOrPickAccount(permissionState, accountPicker)
    }

    companion object {
        @OptIn(ExperimentalPermissionsApi::class)
        fun launchPermissionRequestOrPickAccount(
            permissionState: PermissionState,
            accountPicker: ManagedActivityResultLauncher<Unit, String>,
        ) {
            when {
                permissionState.status.isGranted -> accountPicker.launch(Unit)
                permissionState.status.shouldShowRationale -> TODO()
                else -> permissionState.launchPermissionRequest()
            }
        }
    }
}
