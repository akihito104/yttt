package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.freshdigitable.yttt.data.model.LiveChannel
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
        val item = items[index]
        LazyColumn(
            state = listState[index],
        ) {
            items(
                count = item.itemCount,
                key = { i ->
                    val s = item[i] ?: return@items "$i"
                    s.id.value + s.channel.id.value
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
    }
}
