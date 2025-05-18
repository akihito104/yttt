package com.freshdigitable.yttt.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.freshdigitable.yttt.compose.navigation.AnimatedSharedTransitionScope
import com.freshdigitable.yttt.compose.navigation.NavAnimatedScopedComposable
import com.freshdigitable.yttt.compose.navigation.NavAnimatedScopedComposable.Scope.Companion.asAnimatedSharedTransitionScope
import com.freshdigitable.yttt.compose.navigation.NavParam.Companion.route
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.NavTypedComposable
import com.freshdigitable.yttt.compose.navigation.NavTypedComposable.Companion.liveIdTypeMap
import com.freshdigitable.yttt.compose.navigation.ScopedNavContent
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.create
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.channel.ChannelViewModel
import com.freshdigitable.yttt.feature.timetable.TimetableTabViewModel
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import com.freshdigitable.yttt.lib.R

sealed class MainNavRoute(override val root: String) : NavRoute {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                Subscription,
                ChannelDetail,
                Settings,
                Auth,
            ) + LiveVideoSharedTransitionRoute.routes
        val startDestination: String get() = LiveVideoSharedTransitionRoute.TimetableTab.route
    }

    object Subscription : MainNavRoute("subscription"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_subscription))
            SubscriptionListScreen(
                onListItemClicked = navController::navigate,
                onError = {
                    val message = SnackbarMessage.fromThrowable(it)
                    snackbarBusSender?.send(message)
                }
            )
        }
    }

    object ChannelDetail : MainNavRoute("channel"), NavTypedComposable {
        val SavedStateHandle.toLiveChannelRoute: LiveChannel.Id get() = toRoute(liveIdTypeMap)

        override fun content(): NavGraphBuilder.(ScreenStateHolder) -> Unit = { screenState ->
            composable<LiveChannel.Id>(typeMap = liveIdTypeMap) {
                screenState.topAppBarStateHolder?.update(stringResource(id = R.string.title_channel_detail))
                ChannelDetailScreen(
                    viewModel = hiltViewModel { f: ChannelViewModel.Factory ->
                        f.create(checkNotNull(screenState.snackbarBus))
                    },
                    channelId = it.toRoute(),
                )
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

sealed class LiveVideoSharedTransitionRoute(override val root: String) : MainNavRoute(root) {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                TimetableTab,
                VideoDetail,
            )

        private val LiveVideo.Id.thumbnailTransitionKey: String get() = "img-${type.simpleName}-$value"
        private val LiveVideo.Id.titleTransitionKey: String get() = "title-${type.simpleName}-$value"

        @OptIn(ExperimentalSharedTransitionApi::class)
        private val AnimatedSharedTransitionScope.thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier
            get() = {
                Modifier.Companion.sharedElement(
                    rememberSharedContentState(key = it.thumbnailTransitionKey), this,
                )
            }

        @OptIn(ExperimentalSharedTransitionApi::class)
        private val AnimatedSharedTransitionScope.titleModifier: @Composable (LiveVideo.Id) -> Modifier
            get() = {
                Modifier.Companion.sharedElement(
                    rememberSharedContentState(key = it.titleTransitionKey), this,
                )
            }
    }

    object TimetableTab : LiveVideoSharedTransitionRoute("ttt"), NavAnimatedScopedComposable {
        override fun body(): ScopedNavContent = {
            asAnimatedSharedTransitionScope {
                TimetableTabScreen(
                    viewModel = hiltViewModel { f: TimetableTabViewModel.Factory ->
                        f.create(checkNotNull(snackbarBusSender))
                    },
                    onListItemClicked = navController::navigate,
                    tabModifier = Modifier
                        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                        .animateEnterExit(
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                        ),
                    thumbnailModifier = thumbnailModifier,
                    titleModifier = titleModifier,
                    topAppBarState = checkNotNull(topAppBarState).also {
                        it.update(title = stringResource(id = R.string.title_timetable))
                    },
                )
            }
        }
    }

    object VideoDetail : LiveVideoSharedTransitionRoute("videoDetail"), NavTypedComposable {
        val SavedStateHandle.toLiveVideoRoute: LiveVideo.Id get() = toRoute(liveIdTypeMap)

        override fun content(): NavGraphBuilder.(ScreenStateHolder) -> Unit = { screenState ->
            composable<LiveVideo.Id>(typeMap = liveIdTypeMap) {
                val scope = NavAnimatedScopedComposable.Scope.create(screenState, this)
                body().invoke(scope, it)
            }
        }

        private fun body(): ScopedNavContent = {
            val topAppBar = requireNotNull(topAppBarState).apply {
                update(title = stringResource(id = R.string.title_stream_detail))
            }
            val id = it.toRoute<LiveVideo.Id>()
            asAnimatedSharedTransitionScope {
                VideoDetailScreen(
                    viewModel = hiltViewModel { f: VideoDetailViewModel.Factory ->
                        f.create(checkNotNull(snackbarBusSender))
                    },
                    thumbnailModifier = thumbnailModifier(id),
                    titleModifier = titleModifier(id).skipToLookaheadSize(),
                    topAppBarStateHolder = topAppBar,
                    onChannelClicked = navController::navigate,
                )
            }
        }
    }
}
