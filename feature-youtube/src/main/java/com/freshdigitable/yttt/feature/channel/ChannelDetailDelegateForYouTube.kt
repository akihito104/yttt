package com.freshdigitable.yttt.feature.channel

import androidx.compose.runtime.Composable
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
