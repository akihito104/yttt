package com.freshdigitable.yttt.feature.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freshdigitable.yttt.data.BuildConfig
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
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.video.createForTwitch
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

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

    override val tabs: List<ChannelDetailPageTab<*>> = listOfNotNull(
        TwitchChannelDetailTab.About,
        TwitchChannelDetailTab.Vod,
        if (BuildConfig.DEBUG) TwitchChannelDetailTab.Debug else null
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

internal sealed class TwitchChannelDetailTab(
    private val title: String,
    private val ordinal: Int,
) : ChannelDetailPageTab<TwitchChannelDetailTab> {
    @Composable
    override fun title(): String = title
    override fun compareTo(other: TwitchChannelDetailTab): Int = ordinal.compareTo(other.ordinal)

    object About : TwitchChannelDetailTab(title = "ABOUT", 0)
    object Vod : TwitchChannelDetailTab(title = "VOD", 1)
    object Debug : TwitchChannelDetailTab(title = "DEBUG", 99)
}

internal class TwitchChannelDetailPageComposableFactory @Inject constructor() :
    ChannelDetailPageComposableFactory {
    override fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable {
        when (tab as TwitchChannelDetailTab) {
            TwitchChannelDetailTab.About -> return {
                val text =
                    delegate.annotatedDetail.collectAsState(initial = AnnotatableString.empty())
                annotatedText { text.value }()
            }

            TwitchChannelDetailTab.Vod -> return {
                val vod = delegate.uploadedVideo.collectAsState(initial = emptyList())
                list(itemProvider = { vod.value }, idProvider = { it.id }) {
                    videoItem(it)()
                }()
            }

            TwitchChannelDetailTab.Debug -> return {
                val detail = delegate.channelDetailBody.collectAsState(initial = null)
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = detail.value?.toString() ?: "",
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Module
@InstallIn(ActivityComponent::class)
internal interface TwitchChannelDetailModule {
    @Binds
    @IdBaseClassKey(TwitchUser.Id::class)
    @IntoMap
    fun bindChannelDetailPageComposableFactory(factory: TwitchChannelDetailPageComposableFactory): ChannelDetailPageComposableFactory
}
