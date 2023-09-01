package com.freshdigitable.yttt.compose

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.TwitchOauthViewModel
import com.freshdigitable.yttt.YouTubeOauthViewModel
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.TwitchOauthToken
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: YouTubeOauthViewModel = hiltViewModel(),
    twitchOauthViewModel: TwitchOauthViewModel = hiltViewModel(),
    twitchToken: TwitchOauthToken? = null,
    onStartLoginTwitch: (String) -> Unit,
    onSetupCompleted: (() -> Unit)?,
) {
    if (twitchToken != null) {
        twitchOauthViewModel.putToken(twitchToken)
    }
    val googleServiceState = viewModel.googleServiceState.collectAsState(initial = null)
    AuthScreen(
        youTubeAuthStateHolder = rememberYouTubeAuthStateHolder(
            pickAccountIntentProvider = viewModel.createPickAccountIntent(),
            googleApiAvailabilityProvider = { viewModel.googleApiAvailability },
            login = { viewModel.login(it) },
            hasGoogleAccount = { viewModel.hasAccount() },
        ),
        twitchAuthStateHolder = rememberTwitchAuthStateHolder(
            authorizeUriProvider = { twitchOauthViewModel.getAuthorizeUrl() },
            hasTwitchToken = twitchOauthViewModel.hasTokenState,
            onStartLoginTwitch = onStartLoginTwitch,
        ),
        googleServiceStateProvider = { googleServiceState.value },
        onSetupCompleted = onSetupCompleted,
    )
}

@Composable
private fun AuthScreen(
    youTubeAuthStateHolder: YouTubeAuthStateHolder,
    twitchAuthStateHolder: TwitchAuthStateHolder,
    googleServiceStateProvider: () -> YouTubeOauthViewModel.AuthState?,
    onSetupCompleted: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        YouTubeListItem(
            holder = youTubeAuthStateHolder,
            googleServiceStateProvider = googleServiceStateProvider,
        )
        TwitchListItem(
            holder = twitchAuthStateHolder,
        )
        val completeButtonEnabled by remember {
            derivedStateOf {
                googleServiceStateProvider() == YouTubeOauthViewModel.AuthState.Succeeded ||
                    twitchAuthStateHolder.hasTwitchTokenProvider()
            }
        }
        if (onSetupCompleted != null) {
            Button(
                enabled = completeButtonEnabled,
                onClick = onSetupCompleted,
            ) {
                Text("complete setup")
            }
        }
    }
}

@Composable
fun YouTubeListItem(
    holder: YouTubeAuthStateHolder,
    googleServiceStateProvider: () -> YouTubeOauthViewModel.AuthState?,
) {
    val googleServiceState = googleServiceStateProvider()
    if (googleServiceState is YouTubeOauthViewModel.AuthState.ServiceConnectionRecoverable) {
        val activity = LocalContext.current as Activity
        holder.showDialog(activity, googleServiceState.code)
    }
    val hasNoAccount = googleServiceState is YouTubeOauthViewModel.AuthState.HasNoAccount
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
private fun YouTubeOauthViewModel.AuthState?.stateText(): String {
    return when (this) {
        YouTubeOauthViewModel.AuthState.HasNoAccount -> "auth"
        YouTubeOauthViewModel.AuthState.ServiceConnectionFailed -> "service denied"
        is YouTubeOauthViewModel.AuthState.ServiceConnectionRecoverable -> ""
        YouTubeOauthViewModel.AuthState.Succeeded -> "connected"
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
fun TwitchListItem(
    holder: TwitchAuthStateHolder,
) {
    val coroutineScope = rememberCoroutineScope()
    val hasTwitchToken = holder.hasTwitchTokenProvider()
    val buttonText = if (hasTwitchToken) "connected" else "auth"
    AuthListItem(title = "Twitch", enabled = !hasTwitchToken, buttonText = buttonText) {
        coroutineScope.launch {
            val authorizeUri = holder.authorizeUriProvider()
            holder.onStartLoginTwitch(authorizeUri)
        }
    }
}

@Composable
private fun rememberTwitchAuthStateHolder(
    authorizeUriProvider: suspend () -> String,
    hasTwitchToken: Flow<Boolean>,
    onStartLoginTwitch: (String) -> Unit,
): TwitchAuthStateHolder {
    val state = hasTwitchToken.collectAsState(initial = false)
    return remember {
        TwitchAuthStateHolder(authorizeUriProvider, state, onStartLoginTwitch)
    }
}

class TwitchAuthStateHolder(
    val authorizeUriProvider: suspend () -> String,
    private val hasTwitchTokenState: State<Boolean>,
    val onStartLoginTwitch: (String) -> Unit,
) {
    val hasTwitchTokenProvider: () -> Boolean = { hasTwitchTokenState.value }
}

@Composable
private fun AuthListItem(
    title: String,
    enabled: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Button(
                enabled = enabled,
                onClick = onClick,
            ) {
                Text(text = buttonText)
            }
        },
    )
}

@LightModePreview
@Composable
private fun AuthScreenPreview() {
    AppTheme {
        AuthScreen(
            youTubeAuthStateHolder = rememberYouTubeAuthStateHolder(
                pickAccountIntentProvider = object : NewChooseAccountIntentProvider {
                    override fun invoke(): Intent = Intent()
                },
                googleApiAvailabilityProvider = { GoogleApiAvailability.getInstance() },
                hasGoogleAccount = { true },
                login = {},
            ),
            twitchAuthStateHolder = rememberTwitchAuthStateHolder(
                authorizeUriProvider = { "" },
                hasTwitchToken = flowOf(false),
                onStartLoginTwitch = {}
            ),
            googleServiceStateProvider = { YouTubeOauthViewModel.AuthState.Succeeded },
            onSetupCompleted = {},
        )
    }
}
