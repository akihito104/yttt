package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.SubscriptionListViewModel
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    page: LivePlatform,
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    val subs = when (page) {
        LivePlatform.YOUTUBE -> viewModel.subscriptions.observeAsState(emptyList())
        LivePlatform.TWITCH -> viewModel.twitchSubs.observeAsState(emptyList())
    }
    ListPage(
        itemProvider = { subs.value },
        onListItemClicked = onListItemClicked,
    )
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
