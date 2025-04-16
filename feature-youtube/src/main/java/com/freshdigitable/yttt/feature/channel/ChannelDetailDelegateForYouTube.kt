package com.freshdigitable.yttt.feature.channel

import androidx.compose.runtime.Composable
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.STATS_SEPARATOR
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.toStringWithComma
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeId
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import java.math.BigInteger
import java.text.DecimalFormat
import java.time.Duration
import java.time.ZoneId

internal class ChannelDetailDelegateForYouTube @AssistedInject constructor(
    private val repository: YouTubeRepository,
    private val channelSectionFacade: YouTubeChannelSectionFacade,
    private val dateTimeProvider: DateTimeProvider,
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
    private val detail: Flow<YouTubeChannelDetail?> = flowOf(id).map { i ->
        repository.fetchChannelList(setOf(i.mapTo())).map { it.firstOrNull() }
            .onFailure { logE(throwable = it) { "detail:$i" } }
            .getOrNull()
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
    override val sections: Flow<List<ChannelSectionItem>> = flowOf(id).transform { i ->
        val section = channelSectionFacade.fetchChannelSection(i.mapTo())
            .onFailure { logE(throwable = it) { "fetchChannelSection:$i" } }
            .getOrDefault(emptyList())
        val taskItems = YouTubeChannelSectionFacade.FetchTaskItems.create(section)
        channelSectionFacade.watchTasks(taskItems)
            .debounce(timeoutMillis = 50)
            .onEach { r ->
                emit(section.map { ChannelSectionItem.create(it, r) }.apply { sorted() })
            }.last().failure.forEach {
                logE(throwable = it) { "channelSectionTask.result:$i" }
            }
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    override val activities: Flow<List<YouTubeChannelLog>> = flowOf(id).map { i ->
        repository.fetchLiveChannelLogs(
            channelId = i.mapTo(),
            publishedAfter = dateTimeProvider.now() - Duration.ofDays(7),
            maxResult = 20,
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    override suspend fun clearForDetail() {
        repository.cleanUp()
    }

    override val debug: Flow<Map<YouTubeChannelDetailPagerContent.DebugId, String>> = combine(
        listOf(
            detail.map { it.toString() },
            uploadedVideo.map { v -> v.joinToString { it.toString() } },
            sections.map { s -> s.joinToString { it.toString() } },
            activities.map { a -> a.joinToString { it.toString() } },
        ).map { it.onStart { emit("") } },
    ) {
        it.mapIndexed { i, s -> YouTubeChannelDetailPagerContent.DebugId("$i") to s }
            .toMap()
    }.stateIn(coroutineScope, SharingStarted.Lazily, emptyMap())
}

internal interface YouTubeChannelDetailPagerContent : ChannelDetailDelegate.PagerContent {
    val uploadedVideo: Flow<List<YouTubePlaylistItem>>
    val sections: Flow<List<ChannelSectionItem>>
    val activities: Flow<List<YouTubeChannelLog>>
    val debug: Flow<Map<DebugId, String>>

    data class DebugId(override val value: String) : YouTubeId
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
