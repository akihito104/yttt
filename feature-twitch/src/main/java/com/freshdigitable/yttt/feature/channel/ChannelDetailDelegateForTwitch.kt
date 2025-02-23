package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.dateFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.video.createForTwitch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId

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
    private val detail: Flow<TwitchUserDetail?> = flowOf(id).map {
        repository.findUsersById(setOf(it.mapTo())).firstOrNull()
    }
    override val channelDetailBody: Flow<LiveChannelDetailBody?> = detail.map { d ->
        d?.let { LiveChannelDetailTwitch(it) }
    }
    override val annotatedDetail: Flow<AnnotatableString> = detail.map { d ->
        val desc = d?.description ?: return@map AnnotatableString.empty()
        AnnotatableString.createForTwitch(desc)
    }
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> = flowOf(id).map { i ->
        repository.fetchVideosByUserId(i.mapTo()).map {
            LiveVideoThumbnailEntity(
                id = it.id.mapTo(),
                thumbnailUrl = it.getThumbnailUrl(),
                title = it.title,
            )
        }
    }
    override val channelSection: Flow<List<ChannelDetailChannelSection>>
        get() = throw AssertionError("unsupported operation")
    override val activities: Flow<List<LiveVideo<*>>>
        get() = throw AssertionError("unsupported operation")
}

internal data class LiveChannelDetailTwitch(
    private val detail: TwitchUserDetail,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : LiveChannelDetailBody {
    override val id: LiveChannel.Id get() = detail.id.mapTo()
    override val statsText: String get() = detail.statsText(zoneId)
    override val title: String get() = detail.displayName
    override val iconUrl: String get() = detail.profileImageUrl
    override val platform: LivePlatform get() = Twitch
    override val bannerUrl: String? get() = null

    companion object {
        private fun TwitchUserDetail.statsText(zoneId: ZoneId): String = listOf(
            loginName,
            "Published:${createdAt.toLocalFormattedText(dateFormatter, zoneId)}",
        ).joinToString(LiveChannelDetailBody.STATS_SEPARATOR)
    }
}
