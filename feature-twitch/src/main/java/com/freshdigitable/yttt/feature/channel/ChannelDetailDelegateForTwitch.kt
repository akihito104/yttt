package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.AnnotatedLiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import com.freshdigitable.yttt.feature.video.createForTwitch
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

    override val tabs: List<ChannelPage> = listOf(
        ChannelPage.ABOUT,
        ChannelPage.UPLOADED,
        ChannelPage.DEBUG_CHANNEL,
    )
    override val channelDetail: Flow<AnnotatedLiveChannelDetail?> = flow {
        val users = repository.findUsersById(setOf(id.mapTo())).firstOrNull()
        val detail = users?.let { u ->
            val d = u.toLiveChannelDetail()
            AnnotatedLiveChannelDetail(
                detail = d,
                annotatedDescription = AnnotatableString.createForTwitch(d.description)
            )
        }
        emit(detail)
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
    override val activities: Flow<List<LiveVideo<*>>>
        get() = throw AssertionError("unsupported operation")
}
