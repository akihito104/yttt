package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.SubscriptionListViewModel
import com.freshdigitable.yttt.data.model.LiveChannel

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    val subs = viewModel.subscriptions.observeAsState().value ?: emptyList()
    LazyColumn {
        itemsIndexed(items = subs, key = { _, item -> item.id.value }) { _, item ->
            LiveChannelListItemView(
                iconUrl = item.channel.iconUrl,
                title = item.channel.title,
                onClick = { onListItemClicked(item.channel.id) },
            )
        }
    }
}
