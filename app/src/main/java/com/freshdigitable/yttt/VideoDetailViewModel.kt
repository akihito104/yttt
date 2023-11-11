package com.freshdigitable.yttt

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelDetailEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val twitchRepository: TwitchLiveRepository,
    private val findLiveVideoTable: Map<LivePlatform, @JvmSuppressWildcards FindLiveVideoUseCase>,
) : ViewModel() {
    fun fetchViewDetail(id: LiveVideo.Id): LiveData<LiveVideo?> {
        return liveData(viewModelScope.coroutineContext) {
            val detail = checkNotNull(findLiveVideoTable[id.platform]).invoke(id)
            if (detail == null) { // TODO: informing video is not found
                emit(null)
                return@liveData
            }
            val channel = when (id.platform) {
                LivePlatform.YOUTUBE -> {
                    val c = repository.fetchChannelList(listOf(detail.channel.id.mapTo()))
                        .first()
                    c.toLiveChannelDetail()
                }

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

private fun YouTubeChannelDetail.toLiveChannelDetail(): LiveChannelDetail = LiveChannelDetailEntity(
    id = id.mapTo(),
    title = title,
    iconUrl = iconUrl,
    bannerUrl = bannerUrl,
    subscriberCount = subscriberCount,
    isSubscriberHidden = isSubscriberHidden,
    viewsCount = videoCount,
    videoCount = viewsCount,
    publishedAt = publishedAt,
    customUrl = customUrl,
    keywords = keywords,
    description = description,
    uploadedPlayList = uploadedPlayList
)
