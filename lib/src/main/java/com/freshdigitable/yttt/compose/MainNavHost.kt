package com.freshdigitable.yttt.compose

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
import com.freshdigitable.yttt.compose.navigation.NavContent.Scope.Companion.asAnimatedSharedTransitionScope
import com.freshdigitable.yttt.compose.navigation.NavParam
import com.freshdigitable.yttt.compose.navigation.NavRoute
import com.freshdigitable.yttt.compose.navigation.ScopedNavContent
import com.freshdigitable.yttt.compose.navigation.TopAppBarStateHolder
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import com.freshdigitable.yttt.lib.R
import kotlinx.coroutines.launch

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

    object Subscription : MainNavRoute("subscription") {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_subscription))
            SubscriptionListScreen(
                onListItemClicked = {
                    val route = ChannelDetail.route(it)
                    navController.navigate(route)
                },
            )
        }
    }

    object ChannelDetail : MainNavRoute("channel"), NavParam.LiveIdPath<LiveChannel.Id> {
        fun getChannelId(savedStateHandle: SavedStateHandle): LiveChannel.Id =
            getLiveId(savedStateHandle) { v, t -> LiveChannel.Id(v, t) }

        override fun body(): ScopedNavContent = {
            topAppBarState?.update(stringResource(id = R.string.title_channel_detail))
            ChannelDetailScreen()
        }
    }

    object Settings : MainNavRoute("settings") {
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(stringResource(id = R.string.title_setting))
            AppSettingsScreen()
        }
    }

    object Auth : MainNavRoute("auth") {
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

    object TimetableTab : LiveVideoSharedTransitionRoute("ttt") {
        @OptIn(ExperimentalSharedTransitionApi::class)
        override fun body(): ScopedNavContent = {
            topAppBarState?.update(title = stringResource(id = R.string.title_timetable))
            asAnimatedSharedTransitionScope {
                TimetableTabScreen(
                    onListItemClicked = {
                        val route = VideoDetail.route(it)
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

    object VideoDetail : LiveVideoSharedTransitionRoute("videoDetail"),
        NavParam.LiveIdPath<LiveVideo.Id> {
        fun getId(savedStateHandle: SavedStateHandle): LiveVideo.Id =
            getLiveId(savedStateHandle) { v, t -> LiveVideo.Id(v, t) }

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
        override fun body(): ScopedNavContent = {
            val viewModel = hiltViewModel<VideoDetailViewModel>()
            val appBarStateHolder = requireNotNull(topAppBarState)
            TopAppBar(viewModel = viewModel, appBarStateHolder = appBarStateHolder)
            val id = viewModel.videoId
            asAnimatedSharedTransitionScope {
                VideoDetailScreen(
                    viewModel = viewModel,
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
                )
            }
        }
    }
}
