package com.freshdigitable.yttt.compose

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import com.freshdigitable.yttt.compose.navigation.LiveIdPathParam
import com.freshdigitable.yttt.compose.navigation.NavArg
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.ScreenStateHolder
import com.freshdigitable.yttt.compose.navigation.TopAppBarStateHolder
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import com.freshdigitable.yttt.lib.R
import kotlinx.coroutines.launch

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
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            screenStateHolder.topAppBarStateHolder?.update(title = stringResource(id = R.string.title_subscription))
            SubscriptionListScreen(
                onListItemClicked = {
                    val route = ChannelDetail.parseRoute(it)
                    screenStateHolder.navController.navigate(route)
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
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            screenStateHolder.topAppBarStateHolder?.update(stringResource(id = R.string.title_channel_detail))
            ChannelDetailScreen()
        }
    }

    object Settings : MainNavRoute(path = "settings") {
        @Composable
        override fun Content(
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            screenStateHolder.topAppBarStateHolder?.update(stringResource(id = R.string.title_setting))
            AppSettingsScreen()
        }
    }

    object Auth : MainNavRoute(path = "auth") {
        @Composable
        override fun Content(
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            screenStateHolder.topAppBarStateHolder?.update(title = stringResource(id = R.string.title_account_setting))
            AuthScreen()
        }
    }
}

sealed class LiveVideoSharedTransitionRoute(path: String) : NavRoute(path) {
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
        override fun Content(
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            screenStateHolder.topAppBarStateHolder?.update(title = stringResource(id = R.string.title_timetable))
            screenStateHolder.animatedSharedTransitionScope(animatedContentScope) {
                val navController = screenStateHolder.navController
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

    object VideoDetail : LiveVideoSharedTransitionRoute(path = "videoDetail") {
        private val liveIdPathParam = LiveIdPathParam<LiveVideo.Id>()
        override val params: Array<NavArg.PathParam<String>> = liveIdPathParam.params

        fun parseRoute(id: LiveVideo.Id): String = super.parseRoute(
            *liveIdPathParam.parseToPathParam(id)
        )

        fun getId(savedStateHandle: SavedStateHandle): LiveVideo.Id {
            return liveIdPathParam.parseToId(savedStateHandle) { v, t -> LiveVideo.Id(v, t) }
        }

        @Composable
        private fun TopAppBar(
            viewModel: VideoDetailViewModel,
            appBarStateHolder: TopAppBarStateHolder,
        ) {
            appBarStateHolder.update(
                title = stringResource(id = R.string.title_stream_detail),
                action = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                    }
                    val items = viewModel.contextMenuItems.collectAsState()
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        items.value.forEach {
                            DropdownMenuItem(
                                text = { Text(text = it.text) },
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.consumeMenuItem(it)
                                    }
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            )
        }

        @ExperimentalSharedTransitionApi
        @Composable
        override fun Content(
            screenStateHolder: ScreenStateHolder,
            animatedContentScope: AnimatedContentScope,
            backStackEntry: NavBackStackEntry
        ) {
            val viewModel = hiltViewModel<VideoDetailViewModel>()
            val appBarStateHolder = requireNotNull(screenStateHolder.topAppBarStateHolder)
            TopAppBar(viewModel = viewModel, appBarStateHolder = appBarStateHolder)
            screenStateHolder.animatedSharedTransitionScope(animatedContentScope) {
                VideoDetailScreen(
                    viewModel = viewModel,
                    thumbnailModifier = {
                        Modifier.Companion.sharedElement(
                            rememberSharedContentState(key = it.thumbnailTransitionKey),
                            this,
                        )
                    },
                    titleModifier = {
                        Modifier.Companion
                            .sharedElement(
                                rememberSharedContentState(key = it.titleTransitionKey),
                                this,
                            )
                            .skipToLookaheadSize()
                    },
                )
            }
        }
    }
}
