package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveSubscription
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
    val icon: ImageView = view.findViewById(R.id.channel_icon)
    val name: TextView = view.findViewById(R.id.channel_name)
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
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.view_channel_item, parent, false)
        return SubscriptionItemHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionItemHolder, position: Int) {
        val item = getItem(position)
        if (item.channel.iconUrl.isNotEmpty()) {
            Glide.with(holder.itemView)
                .load(item.channel.iconUrl)
                .into(holder.icon)
        }
        holder.name.text = item.channel.title
    }
}

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    repository: YouTubeLiveRepository,
) : ViewModel() {
    val subscriptions: LiveData<List<LiveSubscription>> =
        repository.subscriptions.asLiveData(viewModelScope.coroutineContext)
}
