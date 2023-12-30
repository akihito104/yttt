package com.freshdigitable.yttt.compose

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.composableWith

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
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    object Auth : LaunchNavRoute("init_auth") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            Scaffold(
                topBar = { TopAppBar(title = { Text(text = "Account setup") }) }
            ) {
                Column(Modifier.padding(it)) {
                    val nc = rememberNavController()
                    NavHost(navController = nc, startDestination = route) {
                        composableWith(nc, AuthRoute.routes)
                    }
                }
            }
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
