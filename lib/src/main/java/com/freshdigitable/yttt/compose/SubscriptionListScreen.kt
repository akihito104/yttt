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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.feature.subscription.SubscriptionListViewModel

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    val items = viewModel.pagingData.map { v -> v.collectAsLazyPagingItems() }
    val listState = items.indices.map { rememberLazyListState() }
    HorizontalPagerWithTabScreen(
        tabCount = viewModel.tabCount,
        tab = { viewModel.tabText(it, items[it].itemCount) },
    ) { index ->
        SubscriptionListContent(
            item = items[index],
            listState = listState[index],
            onListItemClicked = onListItemClicked,
        )
    }
}

@Composable
private fun SubscriptionListContent(
    item: LazyPagingItems<LiveSubscription>,
    listState: LazyListState = rememberLazyListState(),
    onListItemClicked: (LiveChannel.Id) -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            items(
                count = item.itemCount,
                key = { i ->
                    val s = item[i] ?: return@items "$i"
                    "${s.id.value}_${s.channel.id.value}"
                },
            ) {
                val channel = remember { item[it]?.channel } ?: return@items
                LiveChannelListItemView(
                    iconUrl = channel.iconUrl,
                    title = channel.title,
                    onClick = { onListItemClicked(channel.id) },
                )
            }
        }
        if (!item.loadState.isIdle) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
