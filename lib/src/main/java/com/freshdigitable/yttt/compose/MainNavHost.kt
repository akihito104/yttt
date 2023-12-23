package com.freshdigitable.yttt.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.navDeepLink
import com.freshdigitable.yttt.compose.MainNavRoute.Subscription
import com.freshdigitable.yttt.compose.navigation.LiveIdPathParam
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchOauthToken
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.lib.R
import com.freshdigitable.yttt.logD
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

        @Composable
        override fun title(args: Bundle?): String = stringResource(R.string.title_timetable)
    }

    object Subscription : MainNavRoute(path = "subscription") {
        override val params: Array<Page> = arrayOf(Page)

        object Page : NavArg.PathParam<String> by NavArg.PathParam.string("subscription_page")

        fun parseRoute(page: LivePlatform): String =
            super.parseRoute(Page to page::class.java.name)

        @Suppress("UNCHECKED_CAST")
        fun getPlatform(savedStateHandle: SavedStateHandle): KClass<out LivePlatform> {
            val cls = Page.getValue(savedStateHandle)
            return Class.forName(cls).kotlin as KClass<out LivePlatform>
        }

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            SubscriptionListScreen(
                onListItemClicked = {
                    val route = ChannelDetail.parseRoute(it)
                    navController.navigate(route)
                },
            )
        }

        @Composable
        override fun title(args: Bundle?): String = when (Page.getValue(args)) {
            YouTube::class.java.name -> stringResource(R.string.title_youtube_subscriptions)
            Twitch::class.java.name -> stringResource(R.string.title_twitch_followings)
            else -> throw AssertionError("unsupported platform: ${Page.getValue(args)}")
        }
    }

    object ChannelDetail : MainNavRoute(path = "channel") {
        private val navArgParams = LiveIdPathParam<LiveChannel.Id>()
        override val params: Array<NavArg.PathParam<String>> = navArgParams.params

        fun parseRoute(id: LiveChannel.Id): String =
            super.parseRoute(*navArgParams.parseToPathParam(id))

        fun getChannelId(savedStateHandle: SavedStateHandle): LiveChannel.Id =
            navArgParams.parseToId(savedStateHandle) { v, t -> LiveChannel.Id(v, t) }

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            ChannelDetailScreen()
        }

        @Composable
        override fun title(args: Bundle?): String = stringResource(R.string.title_channel_detail)
    }

    object VideoDetail : MainNavRoute(path = "videoDetail") {
        private val liveIdPathParam = LiveIdPathParam<LiveVideo.Id>()
        override val params: Array<NavArg.PathParam<String>> = liveIdPathParam.params

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(
            *liveIdPathParam.parseToPathParam(id)
        )

        fun getId(savedStateHandle: SavedStateHandle): LiveVideo.Id {
            return liveIdPathParam.parseToId(savedStateHandle) { v, t -> LiveVideo.Id(v, t) }
        }

        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            VideoDetailScreen()
        }

        @Composable
        override fun title(args: Bundle?): String = stringResource(R.string.title_stream_detail)
    }

    object Settings : MainNavRoute(path = "settings") {
        @Composable
        override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) {
            AppSettingsScreen()
        }

        @Composable
        override fun title(args: Bundle?): String = stringResource(R.string.title_setting)
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
            )
        }

        @Composable
        override fun title(args: Bundle?): String = stringResource(R.string.title_account_setting)
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
            logD("TwitchLogin") { "Content: ${backStackEntry.arguments}, $p" }
            check(p.values.all { it != null })

            val token = TwitchOauthToken(
                tokenType = requireNotNull(p[Params.TokenType]),
                state = requireNotNull(p[Params.State]),
                accessToken = requireNotNull(p[Params.AccessToken]),
                scope = requireNotNull(p[Params.Scope]),
            )
            logD("TwitchLogin") { "token: $token" }
//            val authBackStack =
//                checkNotNull(navController.previousBackStackEntry) { "prevDestination: null" }
//            val nextRoute = authBackStack.destination.route
//                check(nextRoute == Auth.route) { "prevDestination: ${authBackStack.destination}" }
            TwitchAuthRedirectionDialog(
                token,
                onDismiss = {
                    navController.navigateToAuth(Auth.Modes.MENU) // TODO
                }
            )
        }

        private fun Params.getValueFromDeepLinkIntent(bundle: Bundle?): String? {
            if (bundle == null) return null
            val uri = BundleCompat.getParcelable(
                bundle,
                NavController.KEY_DEEP_LINK_INTENT,
                Intent::class.java,
            )?.data ?: return null
            val query = uri.toString().split("#").last()
            logD("MainNavHost") { "getValueFromDeepLinkIntent:${this.argName} $query" }
            return query.split("&").firstOrNull { it.startsWith("${this.argName}=") }
                ?.split("=")?.last()
        }
    }

    @Composable
    override fun title(args: Bundle?): String = stringResource(R.string.title_twitch_authentication)
}

fun NavHostController.navigateToSubscriptionList(page: LivePlatform) =
    navigate(Subscription.parseRoute(page))

fun NavHostController.navigateToAuth(mode: MainNavRoute.Auth.Modes) =
    navigate(MainNavRoute.Auth.parseRoute(mode))
