package com.freshdigitable.yttt.feature.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.STATS_SEPARATOR
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.toStringWithComma
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelSection.Companion.isNestedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.dateFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.create
import com.freshdigitable.yttt.feature.timetable.youtube.BuildConfig
import com.freshdigitable.yttt.feature.video.createForYouTube
import com.freshdigitable.yttt.logE
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoMap
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import java.io.IOException
import java.math.BigInteger
import java.text.DecimalFormat
import java.time.ZoneId

internal class ChannelDetailDelegateForYouTube @AssistedInject constructor(
    private val repository: YouTubeRepository,
    @Assisted id: LiveChannel.Id,
) : ChannelDetailDelegate, YouTubeChannelDetailPagerContent {
    @AssistedFactory
    interface Factory : ChannelDetailDelegate.Factory {
        override fun create(id: LiveChannel.Id): ChannelDetailDelegateForYouTube
    }

    init {
        check(id.type == YouTubeChannel.Id::class) { "unsupported id type: ${id.type}" }
    }

    override val tabs: List<YouTubeChannelDetailTab> = listOfNotNull(
        YouTubeChannelDetailTab.About,
        YouTubeChannelDetailTab.Uploaded,
        YouTubeChannelDetailTab.Sections,
        YouTubeChannelDetailTab.Actions,
        if (BuildConfig.DEBUG) YouTubeChannelDetailTab.Debug else null
    )
    private val detail: Flow<YouTubeChannelDetail?> = flowOf(id).map {
        repository.fetchChannelList(setOf(it.mapTo())).firstOrNull()
    }
    override val channelDetailBody: Flow<LiveChannelDetailBody?> = detail.map { d ->
        d?.let { LiveChannelDetailYouTube(it) }
    }
    override val pagerContent: ChannelDetailDelegate.PagerContent
        get() = this
    override val annotatedDetail: Flow<AnnotatableString> = detail.map { d ->
        val desc = d?.description ?: return@map AnnotatableString.empty()
        AnnotatableString.createForYouTube(desc)
    }
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> = detail.map { d ->
        val pId = d?.uploadedPlayList ?: return@map emptyList()
        try {
            repository.fetchPlaylistItems(pId, maxResult = 20).map { it.toLiveVideoThumbnail() }
        } catch (e: Exception) {
            logE(throwable = e) { "detail:$d" }
            emptyList()
        }
    }

    override val sections: Flow<List<ChannelDetailChannelSection>> =
        flowOf(id).transform { i ->
            val sections = repository.fetchChannelSection(i.mapTo())
                .map { cs -> fetchSectionItems(cs) }
            combine(sections) { it.apply { sort() }.toList() }
                .collect { emit(it) }
        }

    @OptIn(FlowPreview::class)
    private suspend fun fetchSectionItems(cs: YouTubeChannelSection): Flow<ChannelDetailChannelSection> {
        val content = cs.content
        val items = flow<ChannelDetailChannelSection.ChannelDetailContent<*>> {
            emit(ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist(emptyList()))
            try {
                emit(fetchSectionItems(content, cs.type))
            } catch (e: IOException) {
                logE(throwable = e) { "fetchChannelSection: error>${cs.title} " }
            }
        }
        val title = flow {
            if (content is YouTubeChannelSection.Content.Playlist &&
                cs.type == YouTubeChannelSection.Type.SINGLE_PLAYLIST
            ) {
                try {
                    val playlist = repository.fetchPlaylist(content.item.toSet())
                    emit(playlist.first().title)
                } catch (e: IOException) {
                    logE(throwable = e) { "fetchChannelSection: error>${cs.title} " }
                }
            } else {
                emit(cs.title ?: cs.type.name)
            }
        }
        return combine(title, items) { t, i ->
            ChannelDetailChannelSection(
                id = cs.id,
                position = cs.position.toInt(),
                title = t,
                content = i,
            )
        }.debounce(timeoutMillis = 200)
    }

    private suspend fun fetchSectionItems(
        content: YouTubeChannelSection.Content<*>?,
        type: YouTubeChannelSection.Type,
    ): ChannelDetailChannelSection.ChannelDetailContent<*> = when (content) {
        is YouTubeChannelSection.Content.Playlist -> {
            if (type.isNestedPlaylist) {
                val playlist = repository.fetchPlaylist(content.item.toSet()).associateBy { it.id }
                val item = content.item.mapNotNull { playlist[it] }
                    .map { it.toLiveVideoThumbnail() }
                ChannelDetailChannelSection.ChannelDetailContent.MultiPlaylist(item)
            } else {
                val item = repository.fetchPlaylistItems(content.item.first(), maxResult = 20)
                    .map { it.toLiveVideoThumbnail() }
                ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist(item)
            }
        }

        is YouTubeChannelSection.Content.Channels -> {
            val channel = repository.fetchChannelList(content.item.toSet()).associateBy { it.id }
            val item = content.item.mapNotNull { channel[it] }
                .map { it.toLiveChannel() }
            ChannelDetailChannelSection.ChannelDetailContent.ChannelList(item)
        }

        else -> {
            ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist(emptyList())
        }
    }

    override val activities: Flow<List<LiveVideo<*>>> = flowOf(id).map { i ->
        val logs = repository.fetchLiveChannelLogs(i.mapTo(), maxResult = 20)
        repository.fetchVideoList(logs.map { it.videoId }.toSet())
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
            .map { LiveVideo.create(it) }
    }

    override suspend fun clearForDetail() {
        repository.cleanUp()
    }
}

internal interface YouTubeChannelDetailPagerContent : ChannelDetailDelegate.PagerContent {
    val uploadedVideo: Flow<List<LiveVideoThumbnail>>
    val sections: Flow<List<ChannelDetailChannelSection>>
    val activities: Flow<List<LiveVideoThumbnail>>
}

internal data class LiveChannelDetailYouTube(
    private val detail: YouTubeChannelDetail,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : LiveChannelDetailBody {
    override val id: LiveChannel.Id get() = detail.id.mapTo()
    override val title: String get() = detail.title
    override val iconUrl: String get() = detail.iconUrl
    override val platform: LivePlatform get() = YouTube
    override val bannerUrl: String? get() = detail.bannerUrl
    override val statsText: String get() = detail.statsText(zoneId)

    companion object {
        private fun YouTubeChannelDetail.statsText(zoneId: ZoneId): String {
            val subscriberCount = if (!isSubscriberHidden)
                "Followers:${subscriberCount.toStringWithUnitPrefix}"
            else null
            return listOfNotNull(
                customUrl,
                subscriberCount,
                "Videos:${videoCount.toStringWithComma}",
                "Views:${viewsCount.toStringWithComma}",
                "Published:${publishedAt.toLocalFormattedText(dateFormatter, zoneId = zoneId)}",
            ).joinToString(STATS_SEPARATOR)
        }

        private val BigInteger.toStringWithUnitPrefix: String
            get() {
                val decimal = this.toBigDecimal()
                val precision = decimal.precision()
                return if (precision <= 3) {
                    this.toString()
                } else {
                    val prefixGrade = (precision - 1) / 3
                    val shift = prefixGrade * 3
                    val digit = DecimalFormat("#.##").format(decimal.movePointLeft(shift))
                    "${digit}${unitPrefix[prefixGrade]}"
                }
            }
        private val unitPrefix = arrayOf("", "k", "M", "G", "T", "P", "E")
    }
}

internal class ChannelDetailChannelSection(
    val id: IdBase,
    val position: Int,
    val title: String,
    val content: ChannelDetailContent<*>?,
) : Comparable<ChannelDetailChannelSection> {
    override fun compareTo(other: ChannelDetailChannelSection): Int =
        position.compareTo(other.position)

    sealed class ChannelDetailContent<T> {
        data class MultiPlaylist(override val item: List<LiveVideoThumbnail>) :
            ChannelDetailContent<LiveVideoThumbnail>()

        data class SinglePlaylist(override val item: List<LiveVideoThumbnail>) :
            ChannelDetailContent<LiveVideoThumbnail>()

        data class ChannelList(override val item: List<LiveChannel>) :
            ChannelDetailContent<LiveChannel>()

        abstract val item: List<T>
    }
}

private fun YouTubePlaylist.toLiveVideoThumbnail(): LiveVideoThumbnail = LiveVideoThumbnailEntity(
    id = id.mapTo(),
    title = title,
    thumbnailUrl = thumbnailUrl,
)

private fun YouTubePlaylistItem.toLiveVideoThumbnail(): LiveVideoThumbnail =
    LiveVideoThumbnailEntity(
        id = id.mapTo(),
        title = title,
        thumbnailUrl = thumbnailUrl,
    )

internal sealed class YouTubeChannelDetailTab(
    private val title: String,
    private val ordinal: Int,
) : ChannelDetailPageTab<YouTubeChannelDetailTab> {
    @Composable
    override fun title(): String = title
    override fun compareTo(other: YouTubeChannelDetailTab): Int = ordinal.compareTo(other.ordinal)

    object About : YouTubeChannelDetailTab(title = "ABOUT", 0)
    object Uploaded : YouTubeChannelDetailTab(title = "UPLOADED", 1)
    object Sections : YouTubeChannelDetailTab(title = "CHANNEL SECTIONS", 2)
    object Actions : YouTubeChannelDetailTab(title = "ACTIVITY", 3)
    object Debug : YouTubeChannelDetailTab(title = "DEBUG", 99)
}

internal object YouTubeChannelDetailPageComposableFactory : ChannelDetailPageComposableFactory {
    override fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable {
        when (tab as YouTubeChannelDetailTab) {
            YouTubeChannelDetailTab.About -> return {
                val desc = delegate.pagerContent.annotatedDetail
                    .collectAsState(AnnotatableString.empty())
                annotatedText { desc.value }()
            }

            YouTubeChannelDetailTab.Actions -> return {
                val logs = (delegate.pagerContent as YouTubeChannelDetailPagerContent)
                    .activities.collectAsState(initial = emptyList())
                list(
                    itemProvider = { logs.value },
                    idProvider = { it.id },
                    content = { videoItem(it)() },
                )()
            }

            YouTubeChannelDetailTab.Sections -> return {
                val sectionState = (delegate.pagerContent as YouTubeChannelDetailPagerContent)
                    .sections.collectAsState(emptyList())
                list(
                    itemProvider = { sectionState.value },
                    idProvider = { it.id },
                    content = { cs -> ChannelSectionContent(cs) },
                )()
            }

            YouTubeChannelDetailTab.Uploaded -> return {
                val itemsState = (delegate.pagerContent as YouTubeChannelDetailPagerContent)
                    .uploadedVideo.collectAsState(emptyList())
                list(
                    itemProvider = { itemsState.value },
                    idProvider = { it.id },
                    content = { videoItem(it)() },
                )()
            }

            YouTubeChannelDetailTab.Debug -> return {
                val text = delegate.channelDetailBody.collectAsState(initial = null)
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = text.value?.toString() ?: "",
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSectionContent(cs: ChannelDetailChannelSection) {
    Column {
        Text(text = cs.title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                val content = cs.content
                items(count = content?.item?.size ?: 0) { i ->
                    when (content) {
                        is ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist ->
                            SinglePlaylistContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.4f),
                            )

                        is ChannelDetailChannelSection.ChannelDetailContent.ChannelList ->
                            MultiChannelContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.3f),
                            )

                        is ChannelDetailChannelSection.ChannelDetailContent.MultiPlaylist ->
                            MultiPlaylistContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.4f),
                            )

                        else -> {}
                    }
                }
            },
        )
    }
}

@Composable
private fun SinglePlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ImageLoadableView.Thumbnail(url = item.thumbnailUrl)
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MultiPlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ImageLoadableView.Thumbnail(url = item.thumbnailUrl)
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MultiChannelContent(item: LiveChannel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            ImageLoadableView.UserIcon(
                url = item.iconUrl,
                size = 48.dp,
            )
        }
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@LightDarkModePreview
@Composable
private fun ChannelListItemPreview() {
    AppTheme {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(5) {
                MultiChannelContent(
                    item = LiveChannelEntity(
                        id = LiveChannel.Id("channel_$it", YouTubeChannel.Id::class),
                        title = "example_$it channel",
                        platform = YouTube,
                        iconUrl = "",
                    ),
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }
        }
    }
}

@Module
@InstallIn(ActivityComponent::class)
internal interface YouTubeChannelDetailModule {
    companion object {
        @IdBaseClassKey(YouTubeChannel.Id::class)
        @IntoMap
        @Provides
        fun provideChannelDetailPageComposeFactory(): ChannelDetailPageComposableFactory =
            YouTubeChannelDetailPageComposableFactory
    }
}
