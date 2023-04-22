package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject

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
