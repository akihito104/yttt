package com.freshdigitable.yttt.compose

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.freshdigitable.yttt.compose.MainNavRoute.ChannelDetail
import com.freshdigitable.yttt.compose.MainNavRoute.Subscription
import com.freshdigitable.yttt.compose.MainNavRoute.TimetableTab
import com.freshdigitable.yttt.compose.MainNavRoute.TwitchLogin
import com.freshdigitable.yttt.compose.MainNavRoute.VideoDetail
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchOauthToken

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
        listOf(
            TimetableTab,
            Subscription,
            ChannelDetail,
            VideoDetail,
            TwitchLogin,
        ).forEach {
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
        arguments = (listOfNotNull(navRoute.pathParam) + (navRoute.queryParams ?: emptyArray()))
            .map { navArg ->
                navArgument(navArg.argName) {
                    type = navArg.type
                    navArg.nullable?.let {
                        this.nullable = it
                        this.defaultValue = navArg.defaultValue
                    }
                }
            },
        deepLinks = navRoute.deepLinks,
        content = { navRoute.Content(navController = navController, backStackEntry = it) },
    )
}

sealed class MainNavRoute(
    val path: String,
) {
    object TimetableTab : MainNavRoute(path = "ttt") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val activity = LocalContext.current as AppCompatActivity
            TimetableTabScreen(
                viewModel = hiltViewModel(viewModelStoreOwner = activity),
                onListItemClicked = {
                    val route = VideoDetail.parseRoute(it)
                    navController.navigate(route)
                },
            )
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
        override val pathParam: NavArg = NavArg.CHANNEL_ID
        fun parseRoute(id: LiveChannel.Id): String =
            super.parseRoute(pathParam = pathParam to id.value)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val arg = requireNotNull(backStackEntry.arguments?.getString(pathParam.argName))
            ChannelDetailScreen(id = LiveChannel.Id(arg))
        }
    }

    object VideoDetail : MainNavRoute(path = "videoDetail") {
        override val pathParam: NavArg = NavArg.VIDEO_ID

        fun parseRoute(id: LiveVideo.Id): String =
            super.parseRoute(pathParam = pathParam to id.value)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val arg = requireNotNull(backStackEntry.arguments?.getString(pathParam.argName))
            VideoDetailScreen(id = LiveVideo.Id(arg))
        }
    }

    object TwitchLogin : MainNavRoute(path = "twitch_login") {
        override val queryParams: Array<out NavArg> = arrayOf(
            NavArg.ACCESS_TOKEN,
            NavArg.SCOPE,
            NavArg.STATE,
            NavArg.TOKEN_TYPE,
        )
        override val deepLinks = listOf(navDeepLink {
            uriPattern = "https://$path/#${queryParams.joinToString("&") { it.argFormat }}"
        })

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val p = queryParams.associateWith { backStackEntry.arguments?.getString(it.argName) }
            val token = if (p.values.all { it != null }) {
                TwitchOauthToken(
                    tokenType = requireNotNull(p[NavArg.TOKEN_TYPE]),
                    state = requireNotNull(p[NavArg.STATE]),
                    accessToken = requireNotNull(p[NavArg.ACCESS_TOKEN]),
                    scope = requireNotNull(p[NavArg.SCOPE]),
                )
            } else null
            TwitchOauthScreen(token = token)
        }
    }

    open val pathParam: NavArg? = null
    open val queryParams: Array<out NavArg>? = null
    open val deepLinks: List<NavDeepLink> = emptyList()

    val route: String
        get() {
            val pp = pathParam?.let { "{${it.argName}}" }
            val p = listOfNotNull(path, pp).joinToString("/")
            val q = queryParams?.joinToString("&") { it.argFormat }
            return listOfNotNull(p, q).joinToString("?")
        }

    fun parseRoute(
        pathParam: Pair<NavArg, Any?>? = null,
        vararg queryParams: Pair<NavArg, Any?>
    ): String {
        val pp = pathParam?.second ?: pathParam?.first?.defaultValue
        val p = listOfNotNull(path, pp).joinToString("/")
        val q = if (queryParams.isEmpty()) {
            null
        } else {
            queryParams.joinToString("&") { "${it.first.argName}=${it.second}" }
        }
        return listOfNotNull(p, q).joinToString("?")
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

    ACCESS_TOKEN("access_token", NavType.StringType, nullable = true),
    TOKEN_TYPE("token_type", NavType.StringType, nullable = true),
    SCOPE("scope", NavType.StringType, nullable = true),
    STATE("state", NavType.StringType, nullable = true),
    ;

    val argFormat: String = "$argName={$argName}"
}
