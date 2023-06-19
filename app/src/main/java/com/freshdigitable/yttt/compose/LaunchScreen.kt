package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.TwitchOauthViewModel
import com.freshdigitable.yttt.YouTubeOauthViewModel

@Composable
fun LaunchScreen(
    viewModel: YouTubeOauthViewModel = hiltViewModel(),
    twitchOauthViewModel: TwitchOauthViewModel = hiltViewModel(),
    onTransition: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        val hasGoogleAccount = viewModel.hasAccount()
        val canLoadList = hasGoogleAccount || twitchOauthViewModel.hasToken()
        if (hasGoogleAccount) {
            viewModel.login()
        }
        onTransition(canLoadList)
    }
}
