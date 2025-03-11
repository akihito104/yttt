package com.freshdigitable.yttt.compose.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.freshdigitable.yttt.compose.TopAppBarStateHolder
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.route
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

interface NavRoute : NavParam, NavContent

abstract class NavActivity(
    override val root: String,
    val activityClass: KClass<out Activity>? = null,
    val action: String? = Intent.ACTION_VIEW,
    val data: Uri? = null,
) : NavRoute

interface NavParam {
    val root: String
    val deepLinks: List<NavDeepLink> get() = emptyList()

    companion object {
        val NavParam.route: String get() = root
    }
}

typealias ScopedNavContent = @Composable NavAnimatedScopedComposable.Scope.(NavBackStackEntry) -> Unit

sealed interface NavContent

interface NavTypedComposable : NavContent {
    fun content(): NavGraphBuilder.(ScreenStateHolder) -> Unit

    companion object {
        val liveIdTypeMap = mapOf(typeOf<KClass<out IdBase>>() to KClassNavType)
        val SavedStateHandle.toLiveVideoRoute: LiveVideo.Id get() = toRoute(liveIdTypeMap)
        val SavedStateHandle.toLiveChannelRoute: LiveChannel.Id get() = toRoute(liveIdTypeMap)
    }

    object KClassNavType : NavType<KClass<out IdBase>>(isNullableAllowed = false) {
        override fun put(bundle: Bundle, key: String, value: KClass<out IdBase>) {
            bundle.putString(key, value.java.name)
        }

        override fun get(bundle: Bundle, key: String): KClass<out IdBase> =
            parseValue(checkNotNull(bundle.getString(key)))

        override fun serializeAsValue(value: KClass<out IdBase>): String = value.java.name

        @Suppress("UNCHECKED_CAST")
        override fun parseValue(value: String): KClass<out IdBase> =
            Class.forName(value).kotlin as KClass<out IdBase>
    }
}

interface NavAnimatedScopedComposable : NavContent {
    interface Scope : AnimatedVisibilityScope {
        val navController: NavHostController
        val topAppBarState: TopAppBarStateHolder?

        @OptIn(ExperimentalSharedTransitionApi::class)
        val sharedTransition: SharedTransitionScope?

        companion object {
            @OptIn(ExperimentalSharedTransitionApi::class)
            inline fun Scope.asAnimatedSharedTransitionScope(
                content: AnimatedSharedTransitionScope.() -> Unit,
            ) {
                AnimatedSharedTransitionScope(requireNotNull(sharedTransition), this).content()
            }
        }
    }

    fun body(): ScopedNavContent
}

@OptIn(ExperimentalSharedTransitionApi::class)
class AnimatedSharedTransitionScope(
    private val sharedTransition: SharedTransitionScope,
    private val animatedContent: AnimatedVisibilityScope,
) : SharedTransitionScope by sharedTransition, AnimatedVisibilityScope by animatedContent

@OptIn(ExperimentalSharedTransitionApi::class)
class ScreenStateHolder(
    val navController: NavHostController,
    val topAppBarStateHolder: TopAppBarStateHolder? = null,
    val sharedTransition: SharedTransitionScope? = null,
)

@OptIn(ExperimentalSharedTransitionApi::class)
internal fun NavAnimatedScopedComposable.Scope.Companion.create(
    screenStateHolder: ScreenStateHolder,
    animatedVisibilityScope: AnimatedVisibilityScope,
): NavAnimatedScopedComposable.Scope =
    object : NavAnimatedScopedComposable.Scope, AnimatedVisibilityScope by animatedVisibilityScope {
        override val navController: NavHostController
            get() = screenStateHolder.navController
        override val topAppBarState: TopAppBarStateHolder?
            get() = screenStateHolder.topAppBarStateHolder
        override val sharedTransition: SharedTransitionScope?
            get() = screenStateHolder.sharedTransition
    }

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

            else -> when (navRoute) {
                is NavAnimatedScopedComposable ->
                    composableWith(screenStateHolder, navRoute, navRoute)

                is NavTypedComposable ->
                    (navRoute as NavTypedComposable).content()(this, screenStateHolder)

                else -> throw IllegalArgumentException("unknown navRoute: $navRoute")
            }
        }
    }
}

private fun NavGraphBuilder.composableWith(
    screenStateHolder: ScreenStateHolder,
    navRoute: NavRoute,
    content: NavAnimatedScopedComposable,
) {
    composable(
        route = navRoute.route,
        deepLinks = navRoute.deepLinks,
        content = {
            val body = content.body()
            val scope = NavAnimatedScopedComposable.Scope.create(screenStateHolder, this)
            scope.body(it)
        },
    )
}
