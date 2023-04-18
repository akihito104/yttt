package com.freshdigitable.yttt.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.freshdigitable.yttt.SubscriptionListFragmentDirections
import com.freshdigitable.yttt.SubscriptionListViewModel

@Composable
fun SubscriptionListScreen(
    viewModel: SubscriptionListViewModel,
    navController: NavController = rememberNavController()
) {
    val subs = viewModel.subscriptions.observeAsState().value ?: emptyList()
    LazyColumn {
        itemsIndexed(items = subs, key = { _, item -> item.id.value }) { _, item ->
            LiveChannelListItemView(
                iconUrl = item.channel.iconUrl,
                title = item.channel.title,
                onClick = {
                    val action = SubscriptionListFragmentDirections
                        .actionMenuSubscriptionListToChannelFragment(item.channel.id.value)
                    navController.navigate(action)
                },
            )
        }
    }
}
