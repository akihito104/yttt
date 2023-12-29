package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.feature.subscription.SubscriptionListViewModel
import com.freshdigitable.yttt.feature.subscription.SubscriptionTabData

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    val tabs = viewModel.tabData.collectAsState(initial = viewModel.initialTab)
    val lists = viewModel.sources.entries.associate { (p, s) ->
        p to s.collectAsState(initial = emptyList())
    }
    HorizontalPagerWithTabScreen(
        tabDataProvider = { tabs.value }
    ) { tab ->
        val platform = (tab as SubscriptionTabData).platform
        ListPage(
            itemProvider = { checkNotNull(lists[platform]).value },
            onListItemClicked = onListItemClicked,
        )
    }
}

@Composable
private fun ListPage(
    itemProvider: () -> List<LiveSubscription>,
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    LazyColumn {
        itemsIndexed(items = itemProvider(), key = { _, item -> item.id.value }) { _, item ->
            LiveChannelListItemView(
                iconUrl = item.channel.iconUrl,
                title = item.channel.title,
                onClick = { onListItemClicked(item.channel.id) },
            )
        }
    }
}
