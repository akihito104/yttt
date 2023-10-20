package com.freshdigitable.yttt.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.freshdigitable.yttt.TwitchOauthViewModel
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.TwitchOauthToken
import com.freshdigitable.yttt.data.source.TwitchUser
import kotlinx.coroutines.launch

@Composable
fun TwitchOauthScreen(
    viewModel: TwitchOauthViewModel = hiltViewModel(),
    token: TwitchOauthToken? = null,
) {
    if (token != null) {
        viewModel.putToken(token)
    }
    val me = if (viewModel.hasToken()) viewModel.getMe().observeAsState() else null
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    TwitchOauthScreen(
        hasToken = viewModel.hasToken(),
        onLoginClicked = {
            coroutine.launch {
                val uri = viewModel.getAuthorizeUrl()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)
            }
        },
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
