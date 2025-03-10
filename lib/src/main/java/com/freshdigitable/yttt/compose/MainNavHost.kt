package com.freshdigitable.yttt.compose

import android.os.Bundle
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.freshdigitable.yttt.compose.navigation.NavAnimatedScopedComposable
import com.freshdigitable.yttt.compose.navigation.NavAnimatedScopedComposable.Scope.Companion.asAnimatedSharedTransitionScope
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.NavTypedComposable
import com.freshdigitable.yttt.compose.navigation.ScopedNavContent
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.create
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.lib.R
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

sealed class MainNavRoute(override val root: String) : NavRoute {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                Subscription,
                ChannelDetail,
                Settings,
                Auth,
            )
    }

    object Subscription : MainNavRoute("subscription"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_subscription))
            SubscriptionListScreen(
                onListItemClicked = {
                    navController.navigate(it)
                },
            )
        }
    }

    object ChannelDetail : MainNavRoute("channel"), NavTypedComposable {
        override fun content(): NavGraphBuilder.(ScreenStateHolder) -> Unit = { screenState ->
            composable<LiveChannel.Id>(typeMap = navTypeMap) {
                screenState.topAppBarStateHolder?.update(stringResource(id = R.string.title_channel_detail))
                ChannelDetailScreen(channelId = it.toRoute())
            }
        }
    }

    object Settings : MainNavRoute("settings"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(stringResource(id = R.string.title_setting))
            AppSettingsScreen()
        }
    }

    object Auth : MainNavRoute("auth"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_account_setting))
            AuthScreen()
        }
    }
}

sealed class LiveVideoSharedTransitionRoute(override val root: String) : NavRoute {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                TimetableTab,
                VideoDetail,
            )

        private val LiveVideo.Id.thumbnailTransitionKey: String get() = "img-${type.simpleName}-$value"
        private val LiveVideo.Id.titleTransitionKey: String get() = "title-${type.simpleName}-$value"
    }

    object TimetableTab : LiveVideoSharedTransitionRoute("ttt"), NavAnimatedScopedComposable {
        @OptIn(ExperimentalSharedTransitionApi::class)
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_timetable))
            asAnimatedSharedTransitionScope {
                TimetableTabScreen(
                    onListItemClicked = {
                        navController.navigate(it)
                    },
                    tabModifier = Modifier
                        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                        .animateEnterExit(
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                        ),
                    thumbnailModifier = {
                        Modifier.Companion.sharedElement(
                            rememberSharedContentState(key = it.thumbnailTransitionKey),
                            this,
                        )
                    },
                    titleModifier = {
                        Modifier.Companion.sharedElement(
                            rememberSharedContentState(key = it.titleTransitionKey),
                            this,
                        )
                    }
                )
            }
        }
    }

    object VideoDetail : LiveVideoSharedTransitionRoute("videoDetail"), NavTypedComposable {
        @OptIn(ExperimentalSharedTransitionApi::class)
        override fun content(): NavGraphBuilder.(ScreenStateHolder) -> Unit = { screenState ->
            composable<LiveVideo.Id>(typeMap = navTypeMap) {
                val scope = NavAnimatedScopedComposable.Scope.create(screenState, this)
                body().invoke(scope, it)
            }
        }

        @ExperimentalSharedTransitionApi
        private fun body(): ScopedNavContent = {
            val topAppBar = requireNotNull(topAppBarState).apply {
                update(title = stringResource(id = R.string.title_stream_detail))
            }
            val id = it.toRoute<LiveVideo.Id>()
            asAnimatedSharedTransitionScope {
                VideoDetailScreen(
                    thumbnailModifier = Modifier.Companion.sharedElement(
                        rememberSharedContentState(key = id.thumbnailTransitionKey),
                        this,
                    ),
                    titleModifier = Modifier.Companion
                        .sharedElement(
                            rememberSharedContentState(key = id.titleTransitionKey),
                            this,
                        )
                        .skipToLookaheadSize(),
                    topAppBarStateHolder = topAppBar,
                )
            }
        }
    }
}

val KClassType = object : NavType<KClass<out IdBase>>(isNullableAllowed = false) {
    override fun put(bundle: Bundle, key: String, value: KClass<out IdBase>) {
        bundle.putString(key, value.java.name)
    }

    override fun get(bundle: Bundle, key: String): KClass<out IdBase> {
        val name = checkNotNull(bundle.getString(key))
        @Suppress("UNCHECKED_CAST")
        return Class.forName(name).kotlin as KClass<out IdBase>
    }

    override fun serializeAsValue(value: KClass<out IdBase>): String {
        return value.java.name
    }

    @Suppress("UNCHECKED_CAST")
    override fun parseValue(value: String): KClass<out IdBase> {
        return Class.forName(value).kotlin as KClass<out IdBase>
    }
}
val navTypeMap = mapOf(typeOf<KClass<out IdBase>>() to KClassType)
