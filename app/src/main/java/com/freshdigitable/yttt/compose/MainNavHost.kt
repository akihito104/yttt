package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.freshdigitable.yttt.compose.MainNavRoute.ChannelDetail
import com.freshdigitable.yttt.compose.MainNavRoute.Subscription
import com.freshdigitable.yttt.compose.MainNavRoute.TimetableTab
import com.freshdigitable.yttt.compose.MainNavRoute.VideoDetail
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo

@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = TimetableTab.route,
    ) {
        listOf(TimetableTab, Subscription, ChannelDetail, VideoDetail).forEach {
            composableWith(navController, it)
        }
    }
}

fun NavHostController.navigateToSubscriptionList() = navigate(Subscription.route)

fun NavGraphBuilder.composableWith(
    navController: NavHostController,
    navRoute: MainNavRoute,
) {
    composable(
        navRoute.route,
        arguments = navRoute.args.map { navArg ->
            navArgument(navArg.argName) {
                type = navArg.type
                navArg.nullable?.let {
                    this.nullable = it
                    this.defaultValue = navArg.defaultValue
                }
            }
        },
        content = { navRoute.Content(navController = navController, backStackEntry = it) },
    )
}

sealed class MainNavRoute(
    private val path: String,
) {
    object TimetableTab : MainNavRoute(path = "ttt") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            TimetableTabScreen(onListItemClicked = {
                val route = VideoDetail.parseRoute(it)
                navController.navigate(route)
            })
        }
    }

    object Subscription : MainNavRoute(path = "subscription") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            SubscriptionListScreen(onListItemClicked = {
                val route = ChannelDetail.parseRoute(it)
                navController.navigate(route)
            })
        }
    }

    object ChannelDetail : MainNavRoute(path = "channel") {
        override val args: Array<out NavArg> = arrayOf(NavArg.CHANNEL_ID)
        fun parseRoute(id: LiveChannel.Id): String = super.parseRoute(NavArg.CHANNEL_ID to id.value)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val arg = requireNotNull(backStackEntry.arguments?.getString(NavArg.CHANNEL_ID.argName))
            ChannelScreen(id = LiveChannel.Id(arg))
        }
    }

    object VideoDetail : MainNavRoute(path = "videoDetail") {
        override val args: Array<out NavArg> = arrayOf(NavArg.VIDEO_ID)

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(NavArg.VIDEO_ID to id.value)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val arg = requireNotNull(backStackEntry.arguments?.getString(NavArg.VIDEO_ID.argName))
            VideoDetailScreen(id = LiveVideo.Id(arg))
        }
    }

    open val args: Array<out NavArg> = emptyArray()
    val route: String
        get() = listOf(path, *(args.map { "{${it.argName}}" }.toTypedArray())).joinToString("/")

    fun parseRoute(vararg args: Pair<NavArg, Any?>): String {
        val argTable = args.toMap()
        return listOf(path, *(this.args.map { "${argTable[it]}" }.toTypedArray())).joinToString("/")
    }

    @Composable
    abstract fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry)
}

enum class NavArg(
    val argName: String,
    val type: NavType<*>,
    val nullable: Boolean? = null,
    val defaultValue: Any? = null,
) {
    CHANNEL_ID("channelId", NavType.StringType),
    VIDEO_ID("videoId", NavType.StringType),
}
