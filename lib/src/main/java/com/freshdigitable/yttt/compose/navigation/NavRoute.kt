package com.freshdigitable.yttt.compose.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.routeFormat
import kotlin.reflect.KClass

interface NavRoute : NavParam, NavContent

abstract class NavActivity(
    override val root: String,
    val activityClass: KClass<out Activity>? = null,
    val action: String? = Intent.ACTION_VIEW,
    val data: Uri? = null,
) : NavRoute {
    override fun body(): ScopedNavContent = {}
}

@Suppress("UNCHECKED_CAST")
fun <T> NavType<T?>.nonNull(): NavType<T> = this as NavType<T>

fun NavGraphBuilder.composableWith(
    screenStateHolder: ScreenStateHolder,
    navRoutes: Collection<NavRoute>,
) {
    navRoutes.forEach { navRoute ->
        when (navRoute) {
            is NavActivity -> {
                activity(route = navRoute.root) {
                    activityClass = navRoute.activityClass
                    action = navRoute.action
                    data = navRoute.data
                }
            }

            else -> composableWith(screenStateHolder, navRoute)
        }
    }
}

private fun NavGraphBuilder.composableWith(
    screenStateHolder: ScreenStateHolder,
    navRoute: NavRoute,
) {
    composable(
        route = navRoute.routeFormat,
        arguments = navRoute.args.map { navArg ->
            navArgument(navArg.argName) {
                type = navArg.type
                navArg.nullable?.let {
                    this.nullable = it
                    this.defaultValue = navArg.defaultValue
                }
            }
        },
        deepLinks = navRoute.deepLinks,
        content = {
            val body = navRoute.body()
            val scope = NavContent.Scope.create(screenStateHolder, this)
            scope.body(it)
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
)

@OptIn(ExperimentalSharedTransitionApi::class)
private fun NavContent.Scope.Companion.create(
    screenStateHolder: ScreenStateHolder,
    animatedVisibilityScope: AnimatedVisibilityScope,
): NavContent.Scope =
    object : NavContent.Scope, AnimatedVisibilityScope by animatedVisibilityScope {
        override val navController: NavHostController
            get() = screenStateHolder.navController
        override val topAppBarState: TopAppBarStateHolder?
            get() = screenStateHolder.topAppBarStateHolder
        override val sharedTransition: SharedTransitionScope?
            get() = screenStateHolder.sharedTransition
    }
