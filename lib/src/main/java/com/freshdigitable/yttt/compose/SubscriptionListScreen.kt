package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.feature.subscription.SubscriptionListViewModel
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
    onError: suspend (Throwable) -> Unit,
) {
    AppLogger.logD("SubscriptionList") { "start:" }
    val items = viewModel.pagingData.map { v -> v.collectAsLazyPagingItems() }
    val listState: List<LazyListState> = items.indices.map {
        rememberLazyListState()
    }
    HorizontalPagerWithTabScreen(
        tabCount = viewModel.tabCount,
        tab = { index ->
            val i = items[index]
            remember(i.itemCount) {
                viewModel.tabText(index, i.itemCount)
            }
        },
    ) { index ->
        SubscriptionListContent(
            itemProvider = { items[index] },
            listState = listState[index],
            onListItemClicked = { onListItemClicked(it) },
            onError = onError
        )
    }
}

@Composable
private fun SubscriptionListContent(
    itemProvider: () -> LazyPagingItems<LiveSubscription>,
    listState: LazyListState = rememberLazyListState(),
    onListItemClicked: (LiveChannel.Id) -> Unit = {},
    onError: suspend (Throwable) -> Unit = {},
) {
    AppLogger.logD("SubscriptionListContent") { "start:" }
    val item = itemProvider()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(item) {
        if (item.loadState.hasError) {
            val error = item.loadState.run { listOf(refresh, append, prepend) }
                .firstNotNullOfOrNull { it as LoadState.Error }?.error
            if (error != null) {
                coroutineScope.launch {
                    onError(error)
                }
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            items(
                count = item.itemCount,
                key = item.itemKey { i -> "${i.id.value}_${i.channel.id.value}" },
            ) { i ->
                val channel = remember { item[i]?.channel }
                LiveChannelListItemView(
                    modifier = channel?.let { Modifier.clickable { onListItemClicked(it.id) } }
                        ?: Modifier,
                    iconUrl = channel?.iconUrl ?: "",
                    title = channel?.title ?: "",
                )
            }
        }
        if (!item.loadState.isIdle) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun SubscriptionListContentPreview() {
    val items = MutableStateFlow(
        PagingData.from<LiveSubscription>(
            data = (0..10).map {
                LiveSubscriptionEntity(
                    id = LiveSubscription.Id("subs$it", YouTubeSubscription.Id::class),
                    channel = LiveChannelEntity(
                        id = LiveChannel.Id("channel$it", YouTubeChannel.Id::class),
                        title = "channel_$it",
                        iconUrl = "",
                        platform = YouTube,
                    ),
                    order = 0,
                    subscribeSince = Instant.EPOCH,
                )
            },
            sourceLoadStates = LoadStates(
                refresh = LoadState.Loading,
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true),
            ),
        ),
    ).collectAsLazyPagingItems()
    AppTheme {
        SubscriptionListContent(
            itemProvider = { items },
        )
    }
}

private data class LiveSubscriptionEntity(
    override val id: LiveSubscription.Id,
    override val subscribeSince: Instant,
    override val channel: LiveChannel,
    override val order: Int,
) : LiveSubscription
