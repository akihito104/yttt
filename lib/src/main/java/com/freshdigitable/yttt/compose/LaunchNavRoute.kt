package com.freshdigitable.yttt.compose

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.lib.R

sealed class LaunchNavRoute(path: String) : NavRoute(path) {
    companion object {
        val routes: Collection<NavRoute> get() = setOf(Splash, Main, Auth)
    }

    object Splash : LaunchNavRoute("splash") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
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

    object Auth : LaunchNavRoute("init_auth") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
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

    object Main : LaunchNavRoute("main") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            MainScreen()
        }
    }

    @Composable
    override fun title(args: Bundle?): String? = null
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
