package com.freshdigitable.yttt.feature.channel

import androidx.compose.runtime.Composable
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.compose.onFailureWithSnackbarMessage
import com.freshdigitable.yttt.data.BuildConfig
import com.freshdigitable.yttt.data.TwitchRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.DATE
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.data.model.toPattern
import com.freshdigitable.yttt.feature.video.createForTwitch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

internal class ChannelDetailDelegateForTwitch @AssistedInject constructor(
    private val repository: TwitchRepository,
    @Assisted id: LiveChannel.Id,
    @Assisted coroutineScope: CoroutineScope,
    @Assisted private val errorMessageChannel: SnackbarMessageBus.Sender,
) : ChannelDetailDelegate, TwitchChannelDetailPagerContent {
    @AssistedFactory
    interface Factory : ChannelDetailDelegate.Factory {
        override fun create(
            id: LiveChannel.Id,
            coroutineScope: CoroutineScope,
            errorMessageChannel: SnackbarMessageBus.Sender,
        ): ChannelDetailDelegateForTwitch
    }

    init {
        check(id.type == TwitchUser.Id::class) { "unsupported id type: ${id.type}" }
    }

    override val tabs: List<ChannelDetailPageTab<*>> = listOfNotNull(
        TwitchChannelDetailTab.About,
        TwitchChannelDetailTab.Vod,
        if (BuildConfig.DEBUG) TwitchChannelDetailTab.Debug else null,
    )
    private val detail: Flow<TwitchUserDetail?> = flowOf(id).map { i ->
        repository.findUsersById(setOf(i.mapTo()))
            .onFailureWithSnackbarMessage(errorMessageChannel)
            .getOrNull()?.firstOrNull()?.item
    }
    override val channelDetailBody: Flow<LiveChannelDetailBody?> = detail.map { d ->
        d?.let { LiveChannelDetailTwitch(it) }
    }
    override val pagerContent: ChannelDetailDelegate.PagerContent
        get() = this
    override val annotatedDetail: Flow<AnnotatableString> = detail.map { d ->
        val desc = d?.description ?: return@map AnnotatableString.empty()
        AnnotatableString.createForTwitch(desc)
    }
    override val vod: Flow<List<TwitchVideoDetail>> = flowOf(id).mapNotNull { i ->
        repository.fetchVideosByUserId(i.mapTo())
            .onFailureWithSnackbarMessage(errorMessageChannel)
            .getOrNull()?.map { it.item }
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())
    override val debug: Flow<Map<TwitchChannelDetailPagerContent.DebugId, String>> = combine(
        listOf(
            detail.map { it.toString() },
            vod.map { v -> v.joinToString { it.toString() } },
        ).map { it.onStart { emit("") } },
    ) {
        it.mapIndexed { i, s -> TwitchChannelDetailPagerContent.DebugId("$i") to s }
            .toMap()
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())
}

internal interface TwitchChannelDetailPagerContent : ChannelDetailDelegate.PagerContent {
    val vod: Flow<List<TwitchVideoDetail>>
    val debug: Flow<Map<DebugId, String>>

    data class DebugId(override val value: String) : TwitchId
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
            "Published:${createdAt.toLocalFormattedText(formatter = DATE.toPattern(), zoneId = zoneId)}",
        ).joinToString(LiveChannelDetailBody.STATS_SEPARATOR)
    }
}

internal sealed class TwitchChannelDetailTab(
    private val title: String,
    private val ordinal: Int,
) : ChannelDetailPageTab<TwitchChannelDetailTab> {
    @Composable
    override fun title(): String = title
    override fun compareTo(other: TwitchChannelDetailTab): Int = ordinal.compareTo(other.ordinal)

    data object About : TwitchChannelDetailTab(title = "ABOUT", ordinal = 0)
    data object Vod : TwitchChannelDetailTab(title = "VOD", ordinal = 1)
    data object Debug : TwitchChannelDetailTab(title = "DEBUG", ordinal = 99)
}
