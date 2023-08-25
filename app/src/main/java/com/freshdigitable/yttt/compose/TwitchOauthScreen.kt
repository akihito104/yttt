package com.freshdigitable.yttt.compose

import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_NO
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.TwitchOauthViewModel
import com.freshdigitable.yttt.data.TwitchOauthToken
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlaylist
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.Instant

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
    userProvider: () -> LiveChannelDetail?,
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
            Text(text = user?.title ?: "")
            Text(text = user?.customUrl ?: "")
            if (user?.iconUrl?.isNotEmpty() == true) {
                GlideImage(model = user.iconUrl, contentDescription = "")
            }
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun TwitchOauthScreenPreview() {
    AppTheme {
        TwitchOauthScreen(
            hasToken = true, onLoginClicked = {}, userProvider = {
                object : LiveChannelDetail {
                    override val id: LiveChannel.Id = LiveChannel.Id("user_id")
                    override val title: String = "display name"
                    override val iconUrl: String = ""
                    override val customUrl: String = "login_name"

                    override fun equals(other: Any?): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun hashCode(): Int {
                        TODO("Not yet implemented")
                    }

                    override val bannerUrl: String?
                        get() = TODO("Not yet implemented")
                    override val subscriberCount: BigInteger
                        get() = TODO("Not yet implemented")
                    override val isSubscriberHidden: Boolean
                        get() = TODO("Not yet implemented")
                    override val videoCount: BigInteger
                        get() = TODO("Not yet implemented")
                    override val viewsCount: BigInteger
                        get() = TODO("Not yet implemented")
                    override val publishedAt: Instant
                        get() = TODO("Not yet implemented")
                    override val keywords: Collection<String>
                        get() = TODO("Not yet implemented")
                    override val description: String?
                        get() = TODO("Not yet implemented")
                    override val uploadedPlayList: LivePlaylist.Id?
                        get() = TODO("Not yet implemented")

                }
            })
    }
}
