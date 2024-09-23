package com.freshdigitable.yttt.compose.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    abstract fun Content(
        screenStateHolder: ScreenStateHolder,
        animatedContentScope: AnimatedContentScope,
        backStackEntry: NavBackStackEntry,
    )
}

abstract class NavActivity(
    override val path: String,
    val activityClass: KClass<out Activity>? = null,
    val action: String? = Intent.ACTION_VIEW,
    val data: Uri? = null,
) : NavR

@Suppress("UNCHECKED_CAST")
fun <T> NavType<T?>.nonNull(): NavType<T> = this as NavType<T>

fun NavGraphBuilder.composableWith(
    screenStateHolder: ScreenStateHolder,
    navRoutes: Collection<NavR>,
) {
    navRoutes.forEach { navRoute ->
        when (navRoute) {
            is NavRoute -> composableWith(screenStateHolder, navRoute)

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

private fun NavGraphBuilder.composableWith(
    screenStateHolder: ScreenStateHolder,
    navRoute: NavRoute,
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
            navRoute.Content(
                screenStateHolder = screenStateHolder,
                backStackEntry = it,
                animatedContentScope = this,
            )
        },
    )
}

interface TopAppBarState {
    val title: String?
    val action: @Composable (RowScope.() -> Unit)? get() = null
}

class TopAppBarStateHolder {
    var state: TopAppBarState? by mutableStateOf(null)
        private set

    fun update(title: String?, action: @Composable (RowScope.() -> Unit)? = null) {
        update(TopAppBarStateImpl(title, action))
    }

    fun update(state: TopAppBarState) {
        this.state = state
    }

    private data class TopAppBarStateImpl(
        override val title: String?,
        override val action: @Composable (RowScope.() -> Unit)?,
    ) : TopAppBarState
}

@OptIn(ExperimentalSharedTransitionApi::class)
class ScreenStateHolder(
    val navController: NavHostController,
    val topAppBarStateHolder: TopAppBarStateHolder? = null,
    val sharedTransition: SharedTransitionScope? = null,
) {
    inline fun animatedSharedTransitionScope(
        animatedContent: AnimatedContentScope,
        content: AnimatedSharedTransitionScope.() -> Unit,
    ) {
        AnimatedSharedTransitionScope(requireNotNull(sharedTransition), animatedContent).content()
    }

    class AnimatedSharedTransitionScope(
        private val sharedTransition: SharedTransitionScope,
        private val animatedContent: AnimatedContentScope
    ) : SharedTransitionScope by sharedTransition, AnimatedVisibilityScope by animatedContent
}
