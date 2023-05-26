package com.freshdigitable.yttt.compose

import android.os.Bundle
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
import com.freshdigitable.yttt.data.model.LivePlatform
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

fun NavHostController.navigateToSubscriptionList(page: LivePlatform) =
    navigate(Subscription.parseRoute(page))

fun NavGraphBuilder.composableWith(
    navController: NavHostController,
    navRoute: MainNavRoute,
) {
    composable(
        navRoute.route,
        arguments = navRoute.params?.map { navArg ->
            navArgument(navArg.argName) {
                type = navArg.type
                navArg.nullable?.let {
                    this.nullable = it
                    this.defaultValue = navArg.defaultValue
                }
            }
        } ?: emptyList(),
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

    open val params: Array<out NavArg<*>>? = null
    private val pathParams: List<NavArg.PathParam<Any>>?
        get() = params?.filterIsInstance<NavArg.PathParam<Any>>()
    private val queryParams: List<NavArg.QueryParam<Any>>?
        get() = params?.filterIsInstance<NavArg.QueryParam<Any>>()
    open val deepLinks: List<NavDeepLink> = emptyList()

    val route: String
        get() {
            val pp = pathParams?.joinToString("/") { it.getArgFormat() }
            val p = listOfNotNull(path, pp).joinToString("/")
            val q = queryParams?.joinToString("&") { it.getArgFormat() }
            return listOfNotNull(p, q).joinToString("?")
        }

    fun parseRoute(vararg params: Pair<NavArg<*>, Any>): String {
        val pp = pathParams?.joinToString("/") { p ->
            val (_, value) = params.first { it.first == p }
            p.parsePath(value)
        }
        val p = listOfNotNull(path, pp).joinToString("/")
        val qp = queryParams
        val q = if (qp.isNullOrEmpty()) {
            null
        } else {
            params.map { (arg, v) -> arg.asNavArg() to v }.filter { qp.contains(it.first) }
                .joinToString("&") { it.first.parsePath(it.second) }
        }
        return listOfNotNull(p, q).joinToString("?")
    }

    @Composable
    abstract fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry)
}

interface NavArg<T> {
    val argName: String
    val type: NavType<T>
    val nullable: Boolean?
    val defaultValue: T?

    fun getValue(bundle: Bundle?): T? {
        if (bundle == null) {
            return null
        }
        return type[bundle, argName]
    }

    fun getArgFormat(): String
    fun parsePath(value: T): String = if (value is Enum<*>) value.name else value.toString()

    interface PathParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = null
        override val defaultValue: T? get() = null
        override fun getValue(bundle: Bundle?): T = requireNotNull(super.getValue(bundle))
        override fun getArgFormat(): String = "{$argName}"
    }

    interface QueryParam<T> : NavArg<T> {
        override val nullable: Boolean? get() = true
        override fun getArgFormat(): String = "$argName={$argName}"
        override fun parsePath(value: T): String = "$argName=${super.parsePath(value)}"
    }

    @Suppress("UNCHECKED_CAST")
    fun asNavArg(): NavArg<Any> = this as NavArg<Any>
}

@Suppress("UNCHECKED_CAST")
private fun <T> NavType<T?>.nonNull(): NavType<T> = this as NavType<T>
