package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo

private const val ROUTE_TIMETABLE = "ttt"
private const val ROUTE_SUBSCRIPTION = "subscription"
private const val ARG_CHANNEL_CHANNEL_ID = "channelId"
private const val ROUTE_CHANNEL = "channel/{$ARG_CHANNEL_CHANNEL_ID}"
private fun toChannel(channelId: LiveChannel.Id): String = "channel/${channelId.value}"

@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = ROUTE_TIMETABLE,
    ) {
        composable(ROUTE_TIMETABLE) {
            TimetableTabScreen(onListItemClicked = {
                navController.navigate("videoDetail/${it.value}")
            })
        }
        composable(ROUTE_SUBSCRIPTION) {
            SubscriptionListScreen(onListItemClicked = {
                navController.navigate(toChannel(it))
            })
        }
        composable(
            ROUTE_CHANNEL,
            arguments = listOf(
                navArgument(ARG_CHANNEL_CHANNEL_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val arg = backStackEntry.arguments?.getString(ARG_CHANNEL_CHANNEL_ID)
            checkNotNull(arg)
            ChannelScreen(id = LiveChannel.Id(arg))
        }
        composable(
            "videoDetail/{videoId}",
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
            ),
        ) {
            val arg = it.arguments?.getString("videoId") ?: throw IllegalArgumentException()
            VideoDetailScreen(id = LiveVideo.Id(arg))
        }
    }
}

fun NavHostController.navigateToSubscriptionList() {
    navigate(ROUTE_SUBSCRIPTION)
}
