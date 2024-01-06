package com.freshdigitable.yttt

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.LaunchNavRoute
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.freshdigitable.yttt.feature.oauth.TwitchOauthParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var twitchConsumer: TwitchOauthParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logD { "onCreate(${this}): ${intent.data}" }
        val startDestination = if (isOauthEvent()) LaunchNavRoute.Auth else LaunchNavRoute.Splash
        handleFreeTalkIntent()
        setContent {
            AppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = startDestination.route) {
                    composableWith(navController = navController, navRoutes = LaunchNavRoute.routes)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        logD { "onNewIntent: $intent" }
        super.onNewIntent(intent)
        twitchConsumer.consumeOAuthEvent(intent?.data.toString())
    }

    private fun handleFreeTalkIntent() {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        AddFreeTalkWorker.enqueue(this, text)
    }

    private fun isOauthEvent(): Boolean {
        val url = intent.data?.toString() ?: return false
        return twitchConsumer.consumeOAuthEvent(url)
    }
}
