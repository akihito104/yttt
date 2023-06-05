package com.freshdigitable.yttt.compose

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.AuthViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
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
    AuthScreen(
        pickAccountIntentProvider = { viewModel.createPickAccountIntent() },
        login = { viewModel.login(it) },
        googleApiAvailabilityProvider = { viewModel.googleApiAvailability },
        hasGoogleAccount = { viewModel.hasAccount() },
        googleServiceStateProvider = { googleServiceState.value },
        onSetupCompleted = onSetupCompleted,
    )
}

@Composable
private fun AuthScreen(
    pickAccountIntentProvider: () -> Intent,
    login: (String?) -> Unit,
    googleApiAvailabilityProvider: () -> GoogleApiAvailability,
    hasGoogleAccount: () -> Boolean,
    googleServiceStateProvider: () -> AuthViewModel.AuthState?,
    onSetupCompleted: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        YouTubeListItem(
            pickAccountIntentProvider = pickAccountIntentProvider,
            login = login,
            googleApiAvailabilityProvider = googleApiAvailabilityProvider,
            hasGoogleAccount = hasGoogleAccount,
            googleServiceStateProvider = googleServiceStateProvider,
        )
        TwitchListItem()
        Button(
            enabled = googleServiceStateProvider() is AuthViewModel.AuthState.Succeeded,
            onClick = onSetupCompleted,
        ) {
            Text("complete setup")
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalPermissionsApi::class)
@Composable
fun YouTubeListItem(
    pickAccountIntentProvider: () -> Intent,
    login: (String?) -> Unit,
    googleApiAvailabilityProvider: () -> GoogleApiAvailability,
    hasGoogleAccount: () -> Boolean,
    googleServiceStateProvider: () -> AuthViewModel.AuthState?
) {
    val accountPicker = rememberLauncherForActivityResult(
        contract = pickAccountContract(pickAccountIntentProvider),
    ) {
        if (it.isNotEmpty()) {
            login(it)
        }
    }
    val dialogState = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            if (!hasGoogleAccount()) {
                accountPicker.launch(Unit)
            } else {
                login(null)
            }
        }
    }
    val googleServiceState = googleServiceStateProvider()
    if (googleServiceState is AuthViewModel.AuthState.ServiceConnectionRecoverable) {
        val activity = LocalContext.current as Activity
        googleApiAvailabilityProvider().showErrorDialogFragment(
            activity,
            googleServiceState.code,
            dialogState,
            null,
        )
    }
    val hasNoAccount = googleServiceState is AuthViewModel.AuthState.HasNoAccount
    val permissionState = if (hasNoAccount) {
        rememberPermissionState(permission = Manifest.permission.GET_ACCOUNTS) {
            if (it) accountPicker.launch(Unit)
        }
    } else null
    ListItem(
        text = {
            Text("YouTube")
        },
        trailing = {
            Button(
                enabled = hasNoAccount,
                onClick = {
                    if (googleServiceState == AuthViewModel.AuthState.HasNoAccount) {
                        checkNotNull(permissionState)
                        when {
                            permissionState.status.isGranted -> accountPicker.launch(Unit)
                            permissionState.status.shouldShowRationale -> TODO()
                            else -> permissionState.launchPermissionRequest()
                        }
                    }
                },
            ) {
                Text(googleServiceState.stateText())
            }
        },
    )
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TwitchListItem() {
    ListItem(
        text = { Text("Twitch") },
        trailing = {
            Button(
                enabled = false,
                onClick = { /*TODO*/ },
            ) {
                Text(text = "auth")
            }
        }
    )
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun AuthScreenPreview() {
    MdcTheme {
        AuthScreen(
            hasGoogleAccount = { true },
            pickAccountIntentProvider = { Intent() },
            login = {},
            googleApiAvailabilityProvider = { GoogleApiAvailability.getInstance() },
            googleServiceStateProvider = { null },
            onSetupCompleted = {}
        )
    }
}
