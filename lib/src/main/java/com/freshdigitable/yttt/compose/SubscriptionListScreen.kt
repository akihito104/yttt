package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.feature.subscription.SubscriptionListViewModel

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel = hiltViewModel(),
    onListItemClicked: (LiveChannel.Id) -> Unit,
) {
    HorizontalPagerWithTabScreen(
        viewModel = viewModel,
    ) { tab ->
        val items = viewModel.items(tab).collectAsState(initial = emptyList())
        LazyColumn {
            itemsIndexed(
                items = items.value,
                key = { _, item -> item.id.value },
            ) { _, item ->
                val channel = remember { item.channel }
                LiveChannelListItemView(
                    iconUrl = channel.iconUrl,
                    title = channel.title,
                    onClick = { onListItemClicked(channel.id) },
                )
            }
        }
    }
}
