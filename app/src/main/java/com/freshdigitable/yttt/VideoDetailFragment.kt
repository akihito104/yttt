package com.freshdigitable.yttt

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class VideoDetailFragment : Fragment(R.layout.fragment_video_detail) {
    private val viewModel: VideoDetailViewModel by viewModels()
    private val args: VideoDetailFragmentArgs by navArgs()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val thumbnail = view.findViewById<ImageView>(R.id.videoDetail_thumbnail)
        val title = view.findViewById<TextView>(R.id.videoDetail_title)
        val channelIcon = view.findViewById<ImageView>(R.id.channel_icon)
        val channelName = view.findViewById<TextView>(R.id.channel_name)
        val debug = view.findViewById<TextView>(R.id.videoDetail_debug)

        viewModel.fetchViewDetail(LiveVideo.Id(args.videoId)).observe(viewLifecycleOwner) {
            Glide.with(thumbnail)
                .load(it.thumbnailUrl)
                .placeholder(R.drawable.baseline_smart_display_24)
                .into(thumbnail)
            title.text = it.title
            Glide.with(channelIcon)
                .load(it.channel.iconUrl)
                .into(channelIcon)
            channelName.text = it.channel.title
            debug.text = it.toString()
        }
    }
}

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : ViewModel() {
    fun fetchViewDetail(id: LiveVideo.Id): LiveData<LiveVideo> {
        return liveData(viewModelScope.coroutineContext) {
            val detail = repository.fetchVideoDetail(id)
            val channel = repository.fetchChannelList(listOf(detail.channel.id)).first()
            val res = object : LiveVideo by detail {
                override val channel: LiveChannel
                    get() = channel

                override fun toString(): String = detail.toString()
            }
            emit(res)
        }
    }
}
