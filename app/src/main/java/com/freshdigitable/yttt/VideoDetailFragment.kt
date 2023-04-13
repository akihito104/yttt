package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.freshdigitable.yttt.compose.LiveChannelContentView
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class VideoDetailFragment : Fragment() {
    private val viewModel: VideoDetailViewModel by viewModels()
    private val args: VideoDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video_detail, container, false)
        val item = viewModel.fetchViewDetail(LiveVideo.Id(args.videoId))
        setup(view, item)
        view.findViewById<ComposeView>(R.id.videoDetail_channel).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    val video = item.observeAsState().value ?: return@MdcTheme
                    LiveChannelContentView(
                        iconUrl = video.channel.iconUrl,
                        title = video.channel.title,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        return view
    }

    private fun setup(view: View, item: LiveData<LiveVideo>) {
        val thumbnail = view.findViewById<ImageView>(R.id.videoDetail_thumbnail)
        val title = view.findViewById<TextView>(R.id.videoDetail_title)
        val stats = view.findViewById<TextView>(R.id.videoDetail_stats)
        val description = view.findViewById<TextView>(R.id.videoDetail_description)
        val debug = view.findViewById<TextView>(R.id.videoDetail_debug)

        item.observe(viewLifecycleOwner) {
            Glide.with(thumbnail)
                .load(it.thumbnailUrl)
                .placeholder(R.drawable.baseline_smart_display_24)
                .into(thumbnail)
            title.text = it.title
            debug.text = it.toString()

            val time = if (it.actualStartDateTime != null) {
                "Started:${it.actualStartDateTime!!.toLocalFormattedText(startedFormat)}"
            } else if (it.scheduledStartDateTime != null) {
                "Starting:${it.scheduledStartDateTime!!.toLocalFormattedText(startingFormat)}"
            } else null
            val count =
                if ((it as? LiveVideoDetail)?.viewerCount != null) "Viewers:${it.viewerCount.toString()}"
                else null
            if (time == null && count == null) {
                stats.visibility = View.GONE
            } else {
                stats.text = listOfNotNull(time, count).joinToString("・")
            }

            if (it is LiveVideoDetail) {
                description.text = it.description
            } else {
                description.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val startedFormat = "yyyy/MM/dd(E) HH:mm:ss"
        private const val startingFormat = "yyyy/MM/dd(E) HH:mm"
        private fun Instant.toLocalFormattedText(format: String): String {
            val dateTimeFormatter = DateTimeFormatter.ofPattern(format)
            val localDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
            return localDateTime.format(dateTimeFormatter)
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
            val res = object : LiveVideoDetail, LiveVideo by detail {
                override val description: String
                    get() = (detail as? LiveVideoDetail)?.description ?: ""
                override val viewerCount: BigInteger?
                    get() = (detail as? LiveVideoDetail)?.viewerCount
                override val channel: LiveChannel
                    get() = channel

                override fun toString(): String = detail.toString()
            }
            emit(res)
        }
    }
}
