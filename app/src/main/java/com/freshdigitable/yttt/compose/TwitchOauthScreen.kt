package com.freshdigitable.yttt.compose

import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.freshdigitable.yttt.data.source.TwitchLiveRepository
import com.freshdigitable.yttt.data.source.TwitchOauthToken
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun TwitchOauthScreen(
    viewModel: TwitchOauthViewModel = hiltViewModel(),
    token: TwitchOauthToken? = null,
) {
    if (token != null) {
        viewModel.putToken(token)
    }
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    TwitchOauthScreen(
        onLoginClicked = {
            coroutine.launch {
                val uri = viewModel.login()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)
            }
        },
    )
}

@Composable
private fun TwitchOauthScreen(
    onLoginClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onLoginClicked,
        ) {
            Text(text = "start oauth")
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun TwitchOauthScreenPreview() {
    MdcTheme {
        TwitchOauthScreen(onLoginClicked = {})
    }
}

@HiltViewModel
class TwitchOauthViewModel @Inject constructor(
    private val twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    suspend fun login(): String {
        return twitchRepository.login()
    }

    fun putToken(token: TwitchOauthToken) {
        twitchRepository.putToken(token)
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchOauthViewModel::class.simpleName
    }
}
