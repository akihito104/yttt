package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val twitchRepository: TwitchLiveRepository,
    private val findLiveVideoTable: Map<Class<out IdBase>, @JvmSuppressWildcards FindLiveVideoUseCase>,
) : ViewModel() {
    fun fetchViewDetail(id: LiveVideo.Id): LiveData<LiveVideo?> {
        return liveData(viewModelScope.coroutineContext) {
            val detail = checkNotNull(findLiveVideoTable[id.type.java]).invoke(id)
            if (detail == null) { // TODO: informing video is not found
                emit(null)
                return@liveData
            }
            val channelId = detail.channel.id
            val channel = when (channelId.type) {
                YouTubeChannel.Id::class -> {
                    val c = repository.fetchChannelList(listOf(channelId.mapTo())).first()
                    c.toLiveChannelDetail()
                }

                TwitchUser.Id::class -> {
                    val u = twitchRepository.findUsersById(listOf(channelId.mapTo())).first()
                    u.toLiveChannelDetail()
                }

                else -> throw AssertionError("unsupported type: ${channelId.type}")
            }
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
