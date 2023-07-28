package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.TwitchOauthViewModel
import com.freshdigitable.yttt.YouTubeOauthViewModel
import kotlinx.coroutines.flow.combine

@Composable
fun LaunchScreen(
    viewModel: YouTubeOauthViewModel = hiltViewModel(),
    twitchOauthViewModel: TwitchOauthViewModel = hiltViewModel(),
    onTransition: (Boolean) -> Unit,
) {
    val hasAccount = combine(viewModel.hasAccount, twitchOauthViewModel.hasTokenFlow) { g, t ->
        if (g == null || t == null) null
        else g || t
    }.collectAsState(initial = null)
    if (hasAccount.value == null) {
        return
    }
    LaunchedEffect(hasAccount.value) {
        val hasGoogleAccount = viewModel.hasAccount()
        val canLoadList = hasGoogleAccount || twitchOauthViewModel.hasToken()
        if (hasGoogleAccount) {
            viewModel.login()
        }
        onTransition(canLoadList)
    }
}
