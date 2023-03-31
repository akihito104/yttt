package com.freshdigitable.yttt

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@AndroidEntryPoint
class ChannelFragment : Fragment(R.layout.fragment_channel) {
    private val viewModel: ChannelViewModel by viewModels()
    private val args: ChannelFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val text = view.findViewById<TextView>(R.id.channel_text)
        viewModel.fetchChannel(LiveChannel.Id(args.channelId)).observe(viewLifecycleOwner) {
            text.text = it?.toString()
        }
    }
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : ViewModel() {
    fun fetchChannel(id: LiveChannel.Id): LiveData<LiveChannel?> = flow {
        val channel = repository.fetchChannelList(listOf(id)).firstOrNull()
        emit(channel)
    }.asLiveData(viewModelScope.coroutineContext)
}
