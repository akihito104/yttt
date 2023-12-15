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
import com.freshdigitable.yttt.data.BuildConfig
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logD { "onCreate(${this}): ${intent.data}" }
        val startDestination =
            if (intent.isTwitchOauth) LaunchNavRoute.Main else LaunchNavRoute.Splash
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

    private fun handleFreeTalkIntent() {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        AddFreeTalkWorker.enqueue(this, text)
    }

    companion object {
        private val Intent.isTwitchOauth: Boolean
            get() = data?.toString()?.startsWith(BuildConfig.TWITCH_REDIRECT_URI) == true
    }
}
