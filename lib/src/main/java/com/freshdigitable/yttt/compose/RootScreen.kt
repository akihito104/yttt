package com.freshdigitable.yttt.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.LauncherOption
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.composableWith

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RootScreen(modifier: Modifier = Modifier, launcherOption: LauncherOption? = null) {
    val startDestination = when (launcherOption) {
        LauncherOption.NO_SPLASH -> LaunchNavRoute.Main
        LauncherOption.ON_FINISH_OAUTH -> LaunchNavRoute.Auth
        else -> LaunchNavRoute.Splash
    }
    AppTheme(dynamicColor = true) {
        val navController = rememberNavController()
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination.root,
        ) {
            composableWith(
                screenStateHolder = ScreenStateHolder(navController),
                navRoutes = LaunchNavRoute.routes,
            )
        }
    }
}
