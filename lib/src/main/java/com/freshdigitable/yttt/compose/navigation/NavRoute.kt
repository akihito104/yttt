package com.freshdigitable.yttt.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

abstract class NavRoute(
    internal val path: String,
) {
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
}

@Suppress("UNCHECKED_CAST")
fun <T> NavType<T?>.nonNull(): NavType<T> = this as NavType<T>

fun NavGraphBuilder.composableWith(
    navController: NavHostController,
    navRoutes: Collection<NavRoute>,
) {
    navRoutes.forEach { navRoute ->
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
}
