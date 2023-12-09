package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class ChannelDetailDelegateForTwitch @AssistedInject constructor(
    private val repository: TwitchLiveRepository,
    @Assisted id: LiveChannel.Id,
) : ChannelDetailDelegate {
    @AssistedFactory
    interface Factory : ChannelDetailDelegate.Factory {
        override fun create(id: LiveChannel.Id): ChannelDetailDelegateForTwitch
    }

    init {
        check(id.type == TwitchUser.Id::class) { "unsupported id type: ${id.type}" }
    }

    override val tabs: Array<ChannelPage> = arrayOf(
        ChannelPage.ABOUT,
        ChannelPage.UPLOADED,
        ChannelPage.DEBUG_CHANNEL,
    )
    override val channelDetail: Flow<LiveChannelDetail?> = flow {
        val u = repository.findUsersById(listOf(id.mapTo()))
        emit(u.map { it.toLiveChannelDetail() }.firstOrNull())
    }
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> = flow {
        val res = repository.fetchVideosByUserId(id.mapTo()).map {
            LiveVideoThumbnailEntity(
                id = it.id.mapTo(),
                thumbnailUrl = it.getThumbnailUrl(),
                title = it.title,
            )
        }
        emit(res)
    }
    override val channelSection: Flow<List<ChannelDetailChannelSection>>
        get() = throw AssertionError("unsupported operation")
    override val activities: Flow<List<LiveVideo>>
        get() = throw AssertionError("unsupported operation")
}
