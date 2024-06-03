package com.freshdigitable.yttt.compose.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlin.reflect.KClass

sealed interface NavR {
    val path: String
}

abstract class NavRoute(
    override val path: String,
) : NavR {
    open val params: Array<out NavArg<*>>? = null
    private val pathParams: List<NavArg.PathParam<Any>>?
        get() = params?.filterIsInstance<NavArg.PathParam<Any>>()
    private val queryParams: List<NavArg.QueryParam<Any>>?
        get() = params?.filterIsInstance<NavArg.QueryParam<Any>>()
    open val deepLinks: List<NavDeepLink> = emptyList()

    val route: String
        get() {
            val pp = pathParams?.joinToString("/") { it.getArgFormat() }
            val p = listOfNotNull(path, if (pp.isNullOrEmpty()) null else pp).joinToString("/")
            val q = queryParams?.joinToString("&") { it.getArgFormat() }
            return listOfNotNull(p, if (q.isNullOrEmpty()) null else q).joinToString("?")
        }

    fun parseRoute(vararg params: Pair<NavArg<*>, Any>): String {
        val pp = pathParams?.joinToString("/") { p ->
            val (_, value) = params.first { it.first == p }
            p.parsePath(value)
        }
        val p = listOfNotNull(path, if (pp.isNullOrEmpty()) null else pp).joinToString("/")
        val qp = queryParams
        val q = if (qp.isNullOrEmpty()) {
            null
        } else {
            params.filter { qp.contains(it.first) }
                .joinToString("&") { it.first.asNavArg().parsePath(it.second) }
        }
        return listOfNotNull(p, if (q.isNullOrEmpty()) null else q).joinToString("?")
    }

    @Composable
    abstract fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry)

    @Composable
    abstract fun title(args: Bundle?): String?
}

abstract class NavActivity(
    override val path: String,
    val activityClass: KClass<out Activity>? = null,
    val action: String? = Intent.ACTION_VIEW,
    val data: Uri? = null,
) : NavR

abstract class NavRouteWithSharedTransition(
    override val path: String,
) : NavRoute(path) {
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    abstract fun ContentWithSharedTransition(
        navController: NavHostController,
        backStackEntry: NavBackStackEntry,
        sharedTransition: SharedTransitionScope,
        animatedContentScope: AnimatedContentScope,
    )

    @Composable
    override fun Content(navController: NavHostController, backStackEntry: NavBackStackEntry) =
        throw NotImplementedError()
}

@Suppress("UNCHECKED_CAST")
fun <T> NavType<T?>.nonNull(): NavType<T> = this as NavType<T>

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.composableWith(
    navController: NavHostController,
    navRoutes: Collection<NavR>,
    sharedTransition: SharedTransitionScope? = null,
) {
    navRoutes.forEach { navRoute ->
        when (navRoute) {
            is NavRoute -> composableWith(navController, navRoute, sharedTransition)

            is NavActivity -> {
                activity(route = navRoute.path) {
                    activityClass = navRoute.activityClass
                    action = navRoute.action
                    data = navRoute.data
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavGraphBuilder.composableWith(
    navController: NavHostController,
    navRoute: NavRoute,
    sharedTransition: SharedTransitionScope? = null,
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
        content = {
            if (sharedTransition == null) {
                navRoute.Content(navController = navController, backStackEntry = it)
            } else {
                val nr = navRoute as NavRouteWithSharedTransition
                nr.ContentWithSharedTransition(navController, it, sharedTransition, this@composable)
            }
        },
    )
}
