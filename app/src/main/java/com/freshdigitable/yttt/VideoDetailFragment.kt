package com.freshdigitable.yttt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.freshdigitable.yttt.compose.VideoDetailScreen
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject

@AndroidEntryPoint
class VideoDetailFragment : Fragment() {
    private val viewModel: VideoDetailViewModel by viewModels()
    private val args: VideoDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    VideoDetailScreen(viewModel = viewModel, id = LiveVideo.Id(args.videoId))
                }
            }
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
