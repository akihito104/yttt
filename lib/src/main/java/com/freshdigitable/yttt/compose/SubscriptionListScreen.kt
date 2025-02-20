package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import java.time.Instant

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
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
        )
    }
}

@Composable
private fun SubscriptionListContent(
    itemProvider: () -> LazyPagingItems<LiveSubscription>,
    listState: LazyListState = rememberLazyListState(),
    onListItemClicked: (LiveChannel.Id) -> Unit = {},
) {
    AppLogger.logD("SubscriptionListContent") { "start:" }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val item = itemProvider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            items(
                count = item.itemCount,
                key = item.itemKey { i -> "${i.id.value}_${i.channel.id.value}" },
            ) {
                val channel = remember { item[it]?.channel }
                LiveChannelListItemView(
                    iconUrl = channel?.iconUrl ?: "",
                    title = channel?.title ?: "",
                    onClick = channel?.let { { onListItemClicked(it.id) } } ?: {},
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
    override val order: Int
) : LiveSubscription
