package com.freshdigitable.yttt.compose

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.navDeepLink
import com.freshdigitable.yttt.compose.navigation.LiveIdPathParam
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchOauthToken
import com.freshdigitable.yttt.feature.oauth.TwitchAuthRedirectionDialog
import com.freshdigitable.yttt.lib.R
import com.freshdigitable.yttt.logD

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
        override fun title(args: Bundle?): String = stringResource(R.string.title_subscription)
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
            TwitchAuthRedirectionDialog(
                token,
                onDismiss = {
                    navController.navigate(route)
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
