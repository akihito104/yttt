package com.freshdigitable.yttt.compose

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.freshdigitable.yttt.compose.navigation.LiveIdPathParam
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.NavRouteWithSharedTransition
import com.freshdigitable.yttt.compose.navigation.TopAppBarStateHolder
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.lib.R

sealed class MainNavRoute(path: String) : NavRoute(path) {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                Subscription,
                ChannelDetail,
                Settings,
                Auth,
            )
    }

    object Subscription : MainNavRoute(path = "subscription") {
        @Composable
        override fun Content(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry
        ) {
            topAppBarStateHolder?.update(title = stringResource(id = R.string.title_subscription))
            SubscriptionListScreen(
                onListItemClicked = {
                    val route = ChannelDetail.parseRoute(it)
                    navController.navigate(route)
                },
            )
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
        override fun Content(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry
        ) {
            topAppBarStateHolder?.update(stringResource(id = R.string.title_channel_detail))
            ChannelDetailScreen()
        }
    }

    object Settings : MainNavRoute(path = "settings") {
        @Composable
        override fun Content(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry
        ) {
            topAppBarStateHolder?.update(stringResource(id = R.string.title_setting))
            AppSettingsScreen()
        }
    }

    object Auth : MainNavRoute(path = "auth") {
        @Composable
        override fun Content(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry
        ) {
            topAppBarStateHolder?.update(title = stringResource(id = R.string.title_account_setting))
            AuthScreen()
        }
    }
}

sealed class LiveVideoSharedTransitionRoute(path: String) : NavRouteWithSharedTransition(path) {
    companion object {
        val routes: Collection<NavRoute>
            get() = setOf(
                TimetableTab,
                VideoDetail,
            )

        private val LiveVideo.Id.thumbnailTransitionKey: String get() = "img-${type.simpleName}-$value"
        private val LiveVideo.Id.titleTransitionKey: String get() = "title-${type.simpleName}-$value"
    }

    object TimetableTab : LiveVideoSharedTransitionRoute(path = "ttt") {
        @OptIn(ExperimentalSharedTransitionApi::class)
        @Composable
        override fun ContentWithSharedTransition(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry,
            sharedTransition: SharedTransitionScope,
            animatedContentScope: AnimatedContentScope
        ) {
            topAppBarStateHolder?.update(title = stringResource(id = R.string.title_timetable))
            with(sharedTransition) {
                with(animatedContentScope) {
                    TimetableTabScreen(
                        onListItemClicked = {
                            val route = VideoDetail.parseRoute(it)
                            navController.navigate(route)
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
                                animatedContentScope,
                            )
                        },
                        titleModifier = {
                            Modifier.Companion.sharedElement(
                                rememberSharedContentState(key = it.titleTransitionKey),
                                animatedContentScope,
                            )
                        }
                    )
                }
            }
        }
    }

    object VideoDetail : LiveVideoSharedTransitionRoute(path = "videoDetail") {
        private val liveIdPathParam = LiveIdPathParam<LiveVideo.Id>()
        override val params: Array<NavArg.PathParam<String>> = liveIdPathParam.params

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(
            *liveIdPathParam.parseToPathParam(id)
        )

        fun getId(savedStateHandle: SavedStateHandle): LiveVideo.Id {
            return liveIdPathParam.parseToId(savedStateHandle) { v, t -> LiveVideo.Id(v, t) }
        }

        @ExperimentalSharedTransitionApi
        @Composable
        override fun ContentWithSharedTransition(
            navController: NavHostController,
            topAppBarStateHolder: TopAppBarStateHolder?,
            backStackEntry: NavBackStackEntry,
            sharedTransition: SharedTransitionScope,
            animatedContentScope: AnimatedContentScope,
        ) {
            topAppBarStateHolder?.update(title = stringResource(id = R.string.title_stream_detail))
            with(sharedTransition) {
                VideoDetailScreen(
                    thumbnailModifier = {
                        Modifier.Companion.sharedElement(
                            rememberSharedContentState(key = it.thumbnailTransitionKey),
                            animatedContentScope,
                        )
                    },
                    titleModifier = {
                        Modifier.Companion
                            .sharedElement(
                                rememberSharedContentState(key = it.titleTransitionKey),
                                animatedContentScope,
                            )
                            .skipToLookaheadSize()
                    },
                )
            }
        }
    }
}
