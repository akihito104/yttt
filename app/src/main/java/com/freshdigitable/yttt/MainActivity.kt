package com.freshdigitable.yttt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.LaunchNavRoute
import com.freshdigitable.yttt.compose.navigation.composableWith
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate(${this}): ${intent.data}")
        val startDestination =
            if (intent.isTwitchOauth) LaunchNavRoute.Main else LaunchNavRoute.Splash
        setContent {
            MdcTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = startDestination.route) {
                    composableWith(navController = navController, navRoutes = LaunchNavRoute.routes)
                }
            }
        }
    }

    companion object {
        private val Intent.isTwitchOauth: Boolean
            get() = data?.toString()?.startsWith("https://twitch_login/") == true
    }
}
