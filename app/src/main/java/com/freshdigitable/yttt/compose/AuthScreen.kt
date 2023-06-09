package com.freshdigitable.yttt.compose

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.AuthViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.android.gms.common.GoogleApiAvailability

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onSetupCompleted: () -> Unit,
) {
    if (viewModel.hasAccount()) {
        viewModel.login()
        onSetupCompleted()
        return
    }
    val googleServiceState = viewModel.googleServiceState.collectAsState(initial = null)
    val holder = rememberYouTubeAuthStateHolder(
        pickAccountIntentProvider = { viewModel.createPickAccountIntent() },
        googleApiAvailabilityProvider = { viewModel.googleApiAvailability },
        login = { viewModel.login(it) },
        hasGoogleAccount = { viewModel.hasAccount() },
    )
    AuthScreen(
        holder = holder,
        googleServiceStateProvider = { googleServiceState.value },
        onSetupCompleted = onSetupCompleted,
    )
}

@Composable
private fun AuthScreen(
    holder: YouTubeAuthStateHolder,
    googleServiceStateProvider: () -> AuthViewModel.AuthState?,
    onSetupCompleted: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        YouTubeListItem(
            holder = holder,
            googleServiceStateProvider = googleServiceStateProvider,
        )
        TwitchListItem()
        val completeButtonEnabled by remember {
            derivedStateOf { googleServiceStateProvider() == AuthViewModel.AuthState.Succeeded }
        }
        Button(
            enabled = completeButtonEnabled,
            onClick = onSetupCompleted,
        ) {
            Text("complete setup")
        }
    }
}

@Composable
fun YouTubeListItem(
    holder: YouTubeAuthStateHolder,
    googleServiceStateProvider: () -> AuthViewModel.AuthState?,
) {
    val googleServiceState = googleServiceStateProvider()
    if (googleServiceState is AuthViewModel.AuthState.ServiceConnectionRecoverable) {
        val activity = LocalContext.current as Activity
        holder.showDialog(activity, googleServiceState.code)
    }
    val hasNoAccount = googleServiceState is AuthViewModel.AuthState.HasNoAccount
    AuthListItem(
        title = "YouTube",
        enabled = hasNoAccount,
        buttonText = googleServiceState.stateText(),
    ) {
        if (hasNoAccount) {
            holder.launchPermissionRequestOrPickAccount()
        }
    }
}

@Composable
private fun AuthViewModel.AuthState?.stateText(): String {
    return when (this) {
        AuthViewModel.AuthState.HasNoAccount -> "auth"
        AuthViewModel.AuthState.ServiceConnectionFailed -> "service denied"
        is AuthViewModel.AuthState.ServiceConnectionRecoverable -> ""
        AuthViewModel.AuthState.Succeeded -> "connected"
        else -> "auth"
    }
}

private fun pickAccountContract(
    createIntent: () -> Intent,
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
    pickAccountIntentProvider: () -> Intent,
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

class YouTubeAuthStateHolder @OptIn(ExperimentalPermissionsApi::class) constructor(
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

@Composable
fun TwitchListItem() {
    AuthListItem(title = "Twitch", enabled = false, buttonText = "auth") {}
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AuthListItem(
    title: String,
    enabled: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    ListItem(
        text = { Text(title) },
        trailing = {
            Button(
                enabled = enabled,
                onClick = onClick,
            ) {
                Text(text = buttonText)
            }
        }
    )
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun AuthScreenPreview() {
    MdcTheme {
        AuthScreen(
            holder = rememberYouTubeAuthStateHolder(
                pickAccountIntentProvider = { Intent() },
                googleApiAvailabilityProvider = { GoogleApiAvailability.getInstance() },
                hasGoogleAccount = { true },
                login = {},
            ),
            googleServiceStateProvider = { AuthViewModel.AuthState.Succeeded },
            onSetupCompleted = {},
        )
    }
}
