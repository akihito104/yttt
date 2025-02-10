package com.freshdigitable.yttt.feature.channel

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.AnnotatedLiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import com.freshdigitable.yttt.data.model.toLiveVideoThumbnail
import com.freshdigitable.yttt.feature.create
import com.freshdigitable.yttt.feature.video.createForYouTube
import com.freshdigitable.yttt.logE
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException

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
    override val channelDetail: Flow<AnnotatedLiveChannelDetail?> = flow {
        val list = repository.fetchChannelList(setOf(id.mapTo())).firstOrNull()
        val res = list?.let { c ->
            val d = c.toLiveChannelDetail()
            AnnotatedLiveChannelDetail(
                d,
                AnnotatableString.createForYouTube(d.description ?: ""),
            )
        }
        emit(res)
    }
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> = channelDetail.map { d ->
        val pId = d?.uploadedPlayList ?: return@map emptyList()
        val items = try {
            repository.fetchPlaylistItems(pId, maxResult = 20)
        } catch (e: Exception) {
            return@map emptyList()
        }
        items.map { it.toLiveVideoThumbnail() }
    }

    override val channelSection: Flow<List<ChannelDetailChannelSection>> = flow {
        val sections = repository.fetchChannelSection(id.mapTo())
            .mapNotNull { cs ->
                try {
                    fetchSectionItems(cs)
                } catch (e: IOException) {
                    logE(throwable = e) { "fetchChannelSection: error>${cs.title} " }
                    null
                }
            }
            .sortedBy { it.position }
        emit(sections)
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
            ChannelDetailChannelSection.ChannelDetailContent.ChannelList(item.map { it.toLiveChannelDetail() })
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

    override val activities: Flow<List<LiveVideo<*>>> = flow {
        val logs = repository.fetchLiveChannelLogs(id.mapTo(), maxResult = 20)
        val videos = repository.fetchVideoList(logs.map { it.videoId }.toSet())
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
            .map { LiveVideo.create(it) }
        emit(videos)
    }
}
