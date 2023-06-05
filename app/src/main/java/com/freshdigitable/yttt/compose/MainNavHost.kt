package com.freshdigitable.yttt.compose

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navDeepLink
import com.freshdigitable.yttt.compose.MainNavRoute.Subscription
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.nonNull
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchOauthToken

sealed class MainNavRoute(path: String) : NavRoute(path) {
    companion object {
        val startDestination: NavRoute = Auth
        val routes: Collection<NavRoute> =
            setOf(Auth, TimetableTab, Subscription, ChannelDetail, VideoDetail, TwitchLogin)
    }

    object TimetableTab : MainNavRoute(path = "ttt") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            TimetableTabScreen(
                onListItemClicked = {
                    val route = VideoDetail.parseRoute(it)
                    navController.navigate(route)
                },
            )
        }
    }

    object Subscription : MainNavRoute(path = "subscription") {
        override val params: Array<Page> = arrayOf(Page)

        object Page : NavArg.PathParam<LivePlatform> {
            override val argName: String = "subscription_page"
            override val type: NavType<LivePlatform> = NavType.EnumType(LivePlatform::class.java)
        }

        fun parseRoute(page: LivePlatform): String = super.parseRoute(Page to page)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            SubscriptionListScreen(
                page = Page.getValue(backStackEntry.arguments),
                onListItemClicked = {
                    val route = ChannelDetail.parseRoute(it)
                    navController.navigate(route)
                },
            )
        }
    }

    object ChannelDetail : MainNavRoute(path = "channel") {
        override val params: Array<Params<*>> = arrayOf(Params.Platform, Params.ChannelId)

        sealed class Params<T>(
            override val argName: String,
            override val type: NavType<T>,
        ) : NavArg.PathParam<T> {
            object ChannelId : Params<String>("channel_id", NavType.StringType.nonNull())

            object Platform :
                Params<LivePlatform>("platform", NavType.EnumType(LivePlatform::class.java))
        }

        fun parseRoute(id: LiveChannel.Id): String = super.parseRoute(
            Params.Platform to id.platform, Params.ChannelId to id.value,
        )

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            ChannelDetailScreen(
                id = LiveChannel.Id(
                    value = Params.ChannelId.getValue(backStackEntry.arguments),
                    platform = Params.Platform.getValue(backStackEntry.arguments),
                ),
            )
        }
    }

    object VideoDetail : MainNavRoute(path = "videoDetail") {
        override val params: Array<Params<*>> = arrayOf(Params.Platform, Params.VideoId)

        sealed class Params<T>(
            override val argName: String,
            override val type: NavType<T>,
        ) : NavArg.PathParam<T> {
            object VideoId : Params<String>("video_id", NavType.StringType.nonNull())

            object Platform :
                Params<LivePlatform>("platform", NavType.EnumType(LivePlatform::class.java))
        }

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(
            Params.Platform to id.platform, Params.VideoId to id.value,
        )

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            VideoDetailScreen(
                id = LiveVideo.Id(
                    value = Params.VideoId.getValue(backStackEntry.arguments),
                    platform = Params.Platform.getValue(backStackEntry.arguments),
                )
            )
        }
    }

    object Auth : MainNavRoute(path = "auth") {
        override val params: Array<out NavArg<*>> = emptyArray()

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            AuthScreen(
                onSetupCompleted = {
                    navController.navigate(TimetableTab.route) {
                        popUpTo(Auth.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    object TwitchLogin : MainNavRoute(path = "twitch_login") {
        override val params: Array<Params> = arrayOf(
            Params.AccessToken, Params.TokenType, Params.Scope, Params.State,
        )

        sealed class Params(override val argName: String) : NavArg.QueryParam<String?> {
            object TokenType : Params("token_type")
            object State : Params("state")
            object AccessToken : Params("access_token")
            object Scope : Params("scope")

            override val type: NavType<String?> = NavType.StringType
            override val defaultValue: String? = null
        }

        override val deepLinks = listOf(navDeepLink {
            uriPattern = "https://$path/#${params.joinToString("&") { it.getArgFormat() }}"
        })

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val p = params.associateWith { it.getValue(backStackEntry.arguments) }
            val token = if (p.values.all { it != null }) {
                TwitchOauthToken(
                    tokenType = requireNotNull(p[Params.TokenType]),
                    state = requireNotNull(p[Params.State]),
                    accessToken = requireNotNull(p[Params.AccessToken]),
                    scope = requireNotNull(p[Params.Scope]),
                )
            } else null
            TwitchOauthScreen(token = token)
        }
    }
}

fun NavHostController.navigateToSubscriptionList(page: LivePlatform) =
    navigate(Subscription.parseRoute(page))

fun NavHostController.navigateToTwitchLogin() = navigate(MainNavRoute.TwitchLogin.path)
