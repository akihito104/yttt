package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.STATS_SEPARATOR
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody.Companion.toStringWithComma
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.dateFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannel
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.create
import com.freshdigitable.yttt.feature.video.createForYouTube
import com.freshdigitable.yttt.logE
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigInteger
import java.text.DecimalFormat
import java.time.ZoneId

internal class ChannelDetailDelegateForYouTube @AssistedInject constructor(
    private val repository: YouTubeRepository,
    @Assisted id: LiveChannel.Id,
) : ChannelDetailDelegate {
    @AssistedFactory
    interface Factory : ChannelDetailDelegate.Factory {
        override fun create(id: LiveChannel.Id): ChannelDetailDelegateForYouTube
    }

    init {
        check(id.type == YouTubeChannel.Id::class) { "unsupported id type: ${id.type}" }
    }

    override val tabs: List<ChannelPage> = listOf(
        ChannelPage.ABOUT,
        ChannelPage.CHANNEL_SECTION,
        ChannelPage.UPLOADED,
        ChannelPage.ACTIVITIES,
        ChannelPage.DEBUG_CHANNEL,
    )
    private val detail: Flow<YouTubeChannelDetail?> = flowOf(id).map {
        repository.fetchChannelList(setOf(it.mapTo())).firstOrNull()
    }
    override val channelDetailBody: Flow<LiveChannelDetailBody?> = detail.map { d ->
        d?.let { LiveChannelDetailYouTube(it) }
    }
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

    override val channelSection: Flow<List<ChannelDetailChannelSection>> = flowOf(id).map { i ->
        repository.fetchChannelSection(i.mapTo())
            .mapNotNull { cs ->
                try {
                    fetchSectionItems(cs)
                } catch (e: IOException) {
                    logE(throwable = e) { "fetchChannelSection: error>${cs.title} " }
                    null
                }
            }
            .sortedBy { it.position }
    }

    private suspend fun fetchSectionItems(cs: YouTubeChannelSection): ChannelDetailChannelSection {
        val content = cs.content
        val c = if (content is YouTubeChannelSection.Content.Playlist) {
            if (cs.type == YouTubeChannelSection.Type.MULTIPLE_PLAYLIST ||
                cs.type == YouTubeChannelSection.Type.ALL_PLAYLIST
            ) {
                val item =
                    repository.fetchPlaylist(content.item.toSet()).map { it.toLiveVideoThumbnail() }
                ChannelDetailChannelSection.ChannelDetailContent.MultiPlaylist(item)
            } else {
                val p = repository.fetchPlaylist(content.item.toSet())
                val item = repository.fetchPlaylistItems(content.item.first(), maxResult = 20)
                    .map { it.toLiveVideoThumbnail() }
                return ChannelDetailChannelSection(
                    id = cs.id,
                    position = cs.position.toInt(),
                    title = p.first().title,
                    content = ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist(item),
                )
            }
        } else if (content is YouTubeChannelSection.Content.Channels) {
            val item = repository.fetchChannelList(content.item.toSet())
            ChannelDetailChannelSection.ChannelDetailContent.ChannelList(item.map { it.toLiveChannel() })
        } else {
            ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist(emptyList())
        }
        return ChannelDetailChannelSection(
            id = cs.id,
            position = cs.position.toInt(),
            title = cs.title ?: cs.type.name,
            content = c,
        )
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
