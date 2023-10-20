package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.mapTo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
    private val twitchRepository: TwitchLiveRepository,
    private val findLiveVideoFromTwitch: FindLiveVideoFromTwitchUseCase,
) : ViewModel() {
    fun fetchViewDetail(id: LiveVideo.Id): LiveData<LiveVideo?> {
        return liveData(viewModelScope.coroutineContext) {
            val detail = when (id.platform) {
                LivePlatform.YOUTUBE -> repository.fetchVideoDetail(id)
                LivePlatform.TWITCH -> findLiveVideoFromTwitch(id)
            }
            if (detail == null) { // TODO: informing video is not found
                emit(null)
                return@liveData
            }
            val channel = when (id.platform) {
                LivePlatform.YOUTUBE -> repository.fetchChannelList(listOf(detail.channel.id))
                    .first()

                LivePlatform.TWITCH -> {
                    val tid = detail.channel.id.mapTo<TwitchUser.Id>()
                    val u = twitchRepository.findUsersById(listOf(tid)).first()
                    u.toLiveChannelDetail()
                }
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
