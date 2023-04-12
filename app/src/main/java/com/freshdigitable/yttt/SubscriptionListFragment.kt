package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.findNavController
import com.freshdigitable.yttt.compose.LiveChannelListItemView
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionListFragment : Fragment() {
    private val viewModel: SubscriptionListViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(container?.context ?: return null).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    SubscriptionListScreen(
                        viewModel = viewModel,
                        navController = findNavController(),
                    )
                }
            }
        }
    }
}

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    repository: YouTubeLiveRepository,
) : ViewModel() {
    val subscriptions: LiveData<List<LiveSubscription>> =
        repository.subscriptions.asLiveData(viewModelScope.coroutineContext)
}

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
