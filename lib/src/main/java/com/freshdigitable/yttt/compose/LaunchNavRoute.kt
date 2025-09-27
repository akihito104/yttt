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
import com.freshdigitable.yttt.compose.navigation.NavAnimatedScopedComposable
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.route
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.ScopedNavContent
import com.freshdigitable.yttt.lib.R

sealed class LaunchNavRoute(override val root: String) : NavRoute {
    companion object {
        val routes: Collection<NavRoute> get() = setOf(Splash, Main, Auth)
    }

    object Splash : LaunchNavRoute("splash"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(null)
            LaunchScreen(
                onTransition = { canLoadList ->
                    val route = if (canLoadList) Main.route else Auth.route
                    navController.navigate(route) {
                        popUpTo(Splash.route) {
                            inclusive = true
                        }
                        if (route == Main.route) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }

    object Auth : LaunchNavRoute("init_auth"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(null)
            InitialAccountSettingScreen(
                onComplete = {
                    navController.navigate(Main.route) {
                        popUpTo(route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    object Main : LaunchNavRoute("main"), NavAnimatedScopedComposable {
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
            AuthScreen(onSetupComplete = onComplete)
        }
    }
}
