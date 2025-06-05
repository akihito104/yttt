package com.freshdigitable.yttt

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.LaunchNavRoute
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.composableWith
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logD { "onCreate(${this}): ${intent.data}" }
        setContent {
            RootScreen()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    AppTheme(dynamicColor = true) {
        val navController = rememberNavController()
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = LaunchNavRoute.Splash,
        ) {
            composableWith(
                screenStateHolder = ScreenStateHolder(navController),
                navRoutes = LaunchNavRoute.routes,
            )
        }
    }
}
