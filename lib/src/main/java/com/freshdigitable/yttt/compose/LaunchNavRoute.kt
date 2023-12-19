package com.freshdigitable.yttt.compose

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute

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

        object Destination : NavArg.QueryParam<String> by NavArg.QueryParam.nonNullString(
            "dest", Destinations.AUTH.name,
        )

        enum class Destinations { AUTH, TIMETABLE }

        fun parseRoute(path: Destinations): String = super.parseRoute(Destination to path.name)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val next = Destination.getValue(backStackEntry.arguments)
            val shouldAuth = next == Destinations.AUTH.name
            MainScreen(shouldAuth = shouldAuth)
        }
    }

    @Composable
    override fun title(args: Bundle?): String? = null
}
