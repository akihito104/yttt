package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.nonNull

sealed class LaunchNavRoute(path: String) : NavRoute(path) {
    companion object {
        val routes: Collection<NavRoute> get() = setOf(Splash, Main)
    }

    object Splash : LaunchNavRoute("splash") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            LaunchScreen(
                onTransition = { canLoadList ->
                    val p = if (canLoadList) Main.Destinations.TIMETABLE else Main.Destinations.AUTH
                    val route = Main.parseRoute(p)
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

    object Main : LaunchNavRoute("main") {
        override val params: Array<out NavArg<*>> = arrayOf(Destination)

        object Destination : NavArg.QueryParam<String> {
            override val argName: String = "dest"
            override val type: NavType<String> = NavType.StringType.nonNull()
            override val defaultValue: String = Destinations.AUTH.name
        }

        enum class Destinations { AUTH, TIMETABLE }

        fun parseRoute(path: Destinations): String = super.parseRoute(Destination to path.name)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val next = Destination.getValue(backStackEntry.arguments)
            val shouldAuth = next == Destinations.AUTH.name
            MainScreen(shouldAuth = shouldAuth)
        }
    }
}
