package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.TwitchOauthToken
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.feature.oauth.TwitchOauthViewModel

@Composable
fun TwitchOauthScreen(
    viewModel: TwitchOauthViewModel = hiltViewModel(),
    token: TwitchOauthToken? = null,
) {
    if (token != null) {
        viewModel.putToken(token)
    }
    val me = if (viewModel.hasToken()) viewModel.getMe().observeAsState() else null
    TwitchOauthScreen(
        hasToken = viewModel.hasToken(),
        onLoginClicked = viewModel::onLogin,
        userProvider = { me?.value },
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TwitchOauthScreen(
    hasToken: Boolean,
    onLoginClicked: () -> Unit,
    userProvider: () -> TwitchUser?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!hasToken) {
            Button(
                onClick = onLoginClicked,
            ) {
                Text(text = "start oauth")
            }
        } else {
            val user = userProvider()
            Text(text = user?.id?.value ?: "")
            Text(text = user?.displayName ?: "")
            Text(text = user?.loginName ?: "")
//            if (user?.iconUrl?.isNotEmpty() == true) {
//                GlideImage(model = user.iconUrl, contentDescription = "")
//            }
        }
    }
}

@LightModePreview
@Composable
private fun TwitchOauthScreenPreview() {
    AppTheme {
        TwitchOauthScreen(
            hasToken = true, onLoginClicked = {}, userProvider = {
                object : TwitchUser {
                    override val id: TwitchUser.Id
                        get() = TwitchUser.Id("user_id")
                    override val loginName: String
                        get() = "login_name"
                    override val displayName: String
                        get() = "display_name"
                }
            })
    }
}
