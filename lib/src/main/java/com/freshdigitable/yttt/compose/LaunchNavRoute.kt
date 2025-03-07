package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.routeFormat
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.ScopedNavContent
import com.freshdigitable.yttt.lib.R

sealed class LaunchNavRoute(override val root: String) : NavRoute {
    companion object {
        val routes: Collection<NavRoute> get() = setOf(Splash, Main, Auth)
    }

    object Splash : LaunchNavRoute("splash") {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(null)
            LaunchScreen(
                onTransition = { canLoadList ->
                    val route = if (canLoadList) Main.routeFormat else Auth.routeFormat
                    navController.navigate(route) {
                        popUpTo(Splash.routeFormat) {
                            inclusive = true
                        }
                        if (route == Main.routeFormat) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }

    object Auth : LaunchNavRoute("init_auth") {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(null)
            InitialAccountSettingScreen(
                onComplete = {
                    navController.navigate(Main.routeFormat) {
                        popUpTo(routeFormat) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    object Main : LaunchNavRoute("main") {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(null)
            MainScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitialAccountSettingScreen(
    onComplete: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.title_account_setting)) }) }
    ) {
        Column(Modifier.padding(it)) {
            AuthScreen(onSetupCompleted = onComplete)
        }
    }
}
