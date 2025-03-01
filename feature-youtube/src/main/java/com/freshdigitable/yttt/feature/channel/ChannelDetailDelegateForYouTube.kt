package com.freshdigitable.yttt.feature.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.STATS_SEPARATOR
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.toStringWithComma
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeChannelSection.Companion.isNestedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.dateFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.timetable.youtube.BuildConfig
import com.freshdigitable.yttt.feature.video.createForYouTube
import com.freshdigitable.yttt.logE
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import java.math.BigInteger
import java.text.DecimalFormat
import java.time.ZoneId

internal class ChannelDetailDelegateForYouTube @AssistedInject constructor(
    private val repository: YouTubeRepository,
    @Assisted id: LiveChannel.Id,
    @Assisted coroutineScope: CoroutineScope,
) : ChannelDetailDelegate, YouTubeChannelDetailPagerContent {
    @AssistedFactory
    interface Factory : ChannelDetailDelegate.Factory {
        override fun create(
            id: LiveChannel.Id,
            coroutineScope: CoroutineScope,
        ): ChannelDetailDelegateForYouTube
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
    override val uploadedVideo: Flow<List<YouTubePlaylistItem>> = detail.map { d ->
        val pId = d?.uploadedPlayList ?: return@map emptyList()
        try {
            repository.fetchPlaylistItems(pId, maxResult = 10)
        } catch (e: Exception) {
            logE(throwable = e) { "detail:$d" }
            emptyList()
        }
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    @OptIn(FlowPreview::class)
    override val sections: Flow<List<ChannelSection<*>>> = flowOf(id).transform { i ->
        val sections = repository.fetchChannelSection(i.mapTo())
        val playlistTask = flow {
            val ids = sections.map { it.content }
                .filterIsInstance<YouTubeChannelSection.Content.Playlist>()
                .fold(emptySet<YouTubePlaylist.Id>()) { acc, c -> acc + c.item }
            if (ids.isNotEmpty()) {
                val playlist = repository.fetchPlaylist(ids).associateBy { it.id }
                emit(playlist)
            }
        }.onStart { emit(emptyMap()) }
        val playlistItemTask = flow {
            val ids = sections.filter { it.type == YouTubeChannelSection.Type.SINGLE_PLAYLIST }
                .map { it.content }
                .filterIsInstance<YouTubeChannelSection.Content.Playlist>()
                .fold(emptySet<YouTubePlaylist.Id>()) { acc, c -> acc + c.item }
            if (ids.isNotEmpty()) {
                val t = ids.map { i ->
                    flow {
                        val item = repository.fetchPlaylistItems(i, maxResult = 10)
                        emit(i to item)
                    }
                }
                emitAll(combine(t) { it.toMap() }.debounce(timeoutMillis = 100))
            }
        }.onStart { emit(emptyMap()) }
        val channelTask = flow {
            val ids = sections.map { it.content }
                .filterIsInstance<YouTubeChannelSection.Content.Channels>()
                .fold(emptySet<YouTubeChannel.Id>()) { acc, c -> acc + c.item }
            if (ids.isNotEmpty()) {
                val channel = repository.fetchChannelList(ids).associateBy { it.id }
                emit(channel)
            }
        }.onStart { emit(emptyMap()) }
        val sectionTask = combine(
            playlistTask,
            playlistItemTask,
            channelTask,
        ) { playlist, playlistItem, channel ->
            sections.map { s ->
                val item = when (val content = s.content) {
                    is YouTubeChannelSection.Content.Playlist -> {
                        if (s.type.isNestedPlaylist) {
                            val p = content.item.mapNotNull { playlist[it] }
                            ChannelSection.Item.MultiplePlaylist(p)
                        } else {
                            val p = content.item.firstNotNullOfOrNull { playlist[it] }
                            val items = content.item.firstNotNullOfOrNull { playlistItem[it] }
                            ChannelSection.Item.SinglePlaylist(p, items ?: emptyList())
                        }
                    }

                    is YouTubeChannelSection.Content.Channels -> {
                        val c = content.item.mapNotNull { channel[it] }
                        ChannelSection.Item.ChannelList(c)
                    }

                    else -> ChannelSection.Item.Empty
                }
                ChannelSection(s, item)
            }
        }
        emitAll(sectionTask)
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    override val activities: Flow<List<YouTubeChannelLog>> = flowOf(id).map { i ->
        repository.fetchLiveChannelLogs(i.mapTo(), maxResult = 20)
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    override suspend fun clearForDetail() {
        repository.cleanUp()
    }
}

internal interface YouTubeChannelDetailPagerContent : ChannelDetailDelegate.PagerContent {
    val uploadedVideo: Flow<List<YouTubePlaylistItem>>
    val sections: Flow<List<ChannelSection<*>>>
    val activities: Flow<List<YouTubeChannelLog>>
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

internal class ChannelSection<T : ChannelSection.Item>(
    private val channelSection: YouTubeChannelSection,
    val item: T,
) : Comparable<ChannelSection<*>> {
    val id: YouTubeChannelSection.Id get() = channelSection.id
    val size: Int get() = item.size
    val title: String
        get() = if (item is Item.SinglePlaylist) {
            item.title ?: channelSection.type.name
        } else {
            channelSection.title ?: channelSection.type.name
        }

    override fun compareTo(other: ChannelSection<*>): Int =
        channelSection.compareTo(other.channelSection)

    sealed interface Item {
        val size: Int get() = 0

        data class SinglePlaylist(
            val playlist: YouTubePlaylist?,
            val items: List<YouTubePlaylistItem>,
        ) : Item {
            override val size: Int get() = items.size
            val title: String? get() = playlist?.title
        }

        data class MultiplePlaylist(val items: List<YouTubePlaylist>) : Item {
            override val size: Int get() = items.size
        }

        data class ChannelList(val items: List<YouTubeChannel>) : Item {
            override val size: Int get() = items.size
        }

        data object Empty : Item
    }
}

internal sealed class YouTubeChannelDetailTab(
    private val title: String,
    private val ordinal: Int,
) : ChannelDetailPageTab<YouTubeChannelDetailTab> {
    @Composable
    override fun title(): String = title
    override fun compareTo(other: YouTubeChannelDetailTab): Int =
        ordinal.compareTo(other.ordinal)

    data object About : YouTubeChannelDetailTab(title = "ABOUT", 0)
    data object Uploaded : YouTubeChannelDetailTab(title = "UPLOADED", 1)
    data object Sections : YouTubeChannelDetailTab(title = "CHANNEL SECTIONS", 2)
    data object Actions : YouTubeChannelDetailTab(title = "ACTIVITY", 3)
    data object Debug : YouTubeChannelDetailTab(title = "DEBUG", 99)
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
                    content = { videoItem(it.thumbnailUrl, it.title)() },
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
                    content = { videoItem(it.thumbnailUrl, it.title)() },
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
private fun ChannelSectionContent(cs: ChannelSection<*>) {
    val itemBody: @Composable LazyItemScope.(Int) -> Unit = remember(cs) {
        when (val content = cs.item) {
            is ChannelSection.Item.SinglePlaylist -> return@remember {
                SinglePlaylistContent(
                    item = content.items[it],
                    modifier = Modifier.fillParentMaxWidth(0.4f),
                )
            }

            is ChannelSection.Item.ChannelList -> return@remember {
                MultiChannelContent(
                    item = content.items[it],
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }

            is ChannelSection.Item.MultiplePlaylist -> return@remember {
                MultiPlaylistContent(
                    item = content.items[it],
                    modifier = Modifier.fillParentMaxWidth(0.4f),
                )
            }

            else -> return@remember {}
        }
    }
    Column {
        Text(text = cs.title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { items(count = cs.size, itemContent = itemBody) },
        )
    }
}

@Composable
private fun SinglePlaylistContent(item: YouTubePlaylistItem, modifier: Modifier = Modifier) {
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
private fun MultiPlaylistContent(item: YouTubePlaylist, modifier: Modifier = Modifier) {
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
private fun MultiChannelContent(item: YouTubeChannel, modifier: Modifier = Modifier) {
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
                    item = YouTubeChannelEntity(
                        id = YouTubeChannel.Id("channel_$it"),
                        title = "example_$it channel",
                        iconUrl = "",
                    ),
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }
        }
    }
}
