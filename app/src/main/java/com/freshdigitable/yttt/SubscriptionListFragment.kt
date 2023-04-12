package com.freshdigitable.yttt

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.freshdigitable.yttt.compose.LiveChannelListItemView
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionListFragment : Fragment(R.layout.fragment_subscription_list) {
    private val viewModel: SubscriptionListViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = SubscriptionListAdapter()
        val list = requireNotNull(requireActivity().findViewById<RecyclerView>(R.id.subs_list))
        list.adapter = adapter
        list.layoutManager = LinearLayoutManager(view.context)
        viewModel.subscriptions.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }
    }
}

class SubscriptionItemHolder(view: View) : ViewHolder(view) {
    val composeView: ComposeView = view as ComposeView
}

class SubscriptionListAdapter : ListAdapter<LiveSubscription, SubscriptionItemHolder>(diffUtil) {
    companion object {
        private val diffUtil = object : DiffUtil.ItemCallback<LiveSubscription>() {
            override fun areItemsTheSame(
                oldItem: LiveSubscription,
                newItem: LiveSubscription
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: LiveSubscription,
                newItem: LiveSubscription
            ): Boolean = oldItem == newItem

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionItemHolder {
        val view = ComposeView(parent.context)
        return SubscriptionItemHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionItemHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            val action =
                SubscriptionListFragmentDirections.actionMenuSubscriptionListToChannelFragment(item.channel.id.value)
            it.findNavController().navigate(action)
        }
        holder.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    LiveChannelListItemView(
                        iconUrl = item.channel.iconUrl,
                        title = item.channel.title
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
