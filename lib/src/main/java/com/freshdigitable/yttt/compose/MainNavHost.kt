package com.freshdigitable.yttt.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.BundleCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.navDeepLink
import com.freshdigitable.yttt.compose.MainNavRoute.Subscription
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.data.TwitchOauthToken
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlin.reflect.KClass

sealed class MainNavRoute(path: String) : NavRoute(path) {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                Auth,
                TimetableTab,
                Subscription,
                ChannelDetail,
                VideoDetail,
                TwitchLogin,
                Settings,
            )
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

        object Page : NavArg.PathParam<LivePlatform> by NavArg.PathParam.enum("subscription_page")

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

        sealed class Params<T> : NavArg.PathParam<T> {
            object ChannelId : Params<String>(),
                NavArg.PathParam<String> by NavArg.PathParam.string("channel_id")

            object Platform : Params<String>(),
                NavArg.PathParam<String> by NavArg.PathParam.string("platform")
        }

        fun parseRoute(id: LiveChannel.Id): String = super.parseRoute(
            Params.Platform to id.type.java.name, Params.ChannelId to id.value,
        )

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val type = Params.Platform.getValue(backStackEntry.arguments)
            @Suppress("UNCHECKED_CAST")
            ChannelDetailScreen(
                id = LiveChannel.Id(
                    value = Params.ChannelId.getValue(backStackEntry.arguments),
                    type = Class.forName(type).kotlin as KClass<out IdBase>,
                ),
            )
        }
    }

    object VideoDetail : MainNavRoute(path = "videoDetail") {
        override val params: Array<Params<*>> = arrayOf(Params.Platform, Params.VideoId)

        sealed class Params<T> : NavArg.PathParam<T> {
            object VideoId : Params<String>(),
                NavArg.PathParam<String> by NavArg.PathParam.string("video_id")

            object Platform : Params<String>(),
                NavArg.PathParam<String> by NavArg.PathParam.string("platform")
        }

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(
            Params.Platform to id.type.java.name, Params.VideoId to id.value,
        )

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val type = Params.Platform.getValue(backStackEntry.arguments)
            @Suppress("UNCHECKED_CAST")
            VideoDetailScreen(
                id = LiveVideo.Id(
                    value = Params.VideoId.getValue(backStackEntry.arguments),
                    type = Class.forName(type).kotlin as KClass<out IdBase>,
                )
            )
        }
    }

    object Settings : MainNavRoute(path = "settings") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            AppSettingsScreen()
        }
    }

    object Auth : MainNavRoute(path = "auth") {
        override val params: Array<out NavArg<*>> = arrayOf(Mode)

        object Mode : NavArg.QueryParam<String> by NavArg.QueryParam.nonNullString(
            "mode", Modes.INIT.name,
        )

        enum class Modes { INIT, MENU, }

        fun parseRoute(mode: Modes): String = super.parseRoute(Mode to mode.name)

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val isFromMenu = Mode.getValue(backStackEntry.arguments) == Modes.MENU.name
            Launch(navController = navController, isFromMenu = isFromMenu)
        }

        @Composable
        fun Launch(
            navController: NavHostController,
            isFromMenu: Boolean,
            twitchToken: TwitchOauthToken? = null,
        ) {
            val context = LocalContext.current
            AuthScreen(
                onStartLoginTwitch = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                    context.startActivity(intent)
                },
                onSetupCompleted = if (isFromMenu) {
                    null
                } else {
                    {
                        navController.navigate(TimetableTab.route) {
                            popUpTo(Auth.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                twitchToken = twitchToken,
            )
        }
    }

    object TwitchLogin : MainNavRoute(path = "twitch_login") {
        override val params: Array<Params> = arrayOf(
            Params.AccessToken, Params.Scope, Params.State, Params.TokenType,
        )

        sealed class Params(override val argName: String) :
            NavArg.QueryParam<String?> by NavArg.QueryParam.string(argName) {
            object TokenType : Params("token_type")
            object State : Params("state")
            object AccessToken : Params("access_token")
            object Scope : Params("scope")
        }

        override val deepLinks = listOf(navDeepLink {
            uriPattern = "https://$path/#${params.joinToString("&") { it.getArgFormat() }}"
            action = Intent.ACTION_VIEW
        })

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            val p = params.associateWith {
                it.getValue(backStackEntry.arguments)
                    ?: it.getValueFromDeepLinkIntent(backStackEntry.arguments)
            }
            Log.d("TwitchLogin", "Content: ${backStackEntry.arguments}, $p")
            if (p.values.all { it != null }) {
                val token = TwitchOauthToken(
                    tokenType = requireNotNull(p[Params.TokenType]),
                    state = requireNotNull(p[Params.State]),
                    accessToken = requireNotNull(p[Params.AccessToken]),
                    scope = requireNotNull(p[Params.Scope]),
                )
                Log.d("TwitchLogin", "token: $token")
                val authBackStack =
                    checkNotNull(navController.previousBackStackEntry) { "prevDestination: null" }
                val nextRoute = authBackStack.destination.route
//                check(nextRoute == Auth.route) { "prevDestination: ${authBackStack.destination}" }
                Auth.Launch(
                    navController = navController,
                    isFromMenu = Auth.Mode.getValue(authBackStack.arguments) == Auth.Modes.MENU.name,
                    twitchToken = token,
                )
            } else {
                TwitchOauthScreen()
            }
        }

        private fun Params.getValueFromDeepLinkIntent(bundle: Bundle?): String? {
            if (bundle == null) return null
            val uri = BundleCompat.getParcelable(
                bundle,
                NavController.KEY_DEEP_LINK_INTENT,
                Intent::class.java,
            )?.data ?: return null
            val query = uri.toString().split("#").last()
            Log.d("MainNavHost", "getValueFromDeepLinkIntent:${this.argName} $query")
            return query.split("&").firstOrNull { it.startsWith("${this.argName}=") }
                ?.split("=")?.last()
        }
    }
}

fun NavHostController.navigateToSubscriptionList(page: LivePlatform) =
    navigate(Subscription.parseRoute(page))

fun NavHostController.navigateToAuth(mode: MainNavRoute.Auth.Modes) =
    navigate(MainNavRoute.Auth.parseRoute(mode))
