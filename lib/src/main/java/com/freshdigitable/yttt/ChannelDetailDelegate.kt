package com.freshdigitable.yttt

import android.util.Log
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.LiveVideoThumbnailEntity
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import com.freshdigitable.yttt.data.model.toLiveVideo
import com.freshdigitable.yttt.data.model.toLiveVideoThumbnail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException

interface ChannelDetailDelegate {
    val tabs: Array<ChannelPage>
    val channelDetail: Flow<LiveChannelDetail?>
    val uploadedVideo: Flow<List<LiveVideoThumbnail>>
    val channelSection: Flow<List<ChannelDetailChannelSection>>
    val activities: Flow<List<LiveVideo>>

    interface Factory {
        fun create(id: LiveChannel.Id): ChannelDetailDelegate
    }
}

class ChannelDetailDelegateForYouTube @AssistedInject constructor(
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

    override val tabs: Array<ChannelPage> = arrayOf(
        ChannelPage.ABOUT,
        ChannelPage.CHANNEL_SECTION,
        ChannelPage.UPLOADED,
        ChannelPage.ACTIVITIES,
        ChannelPage.DEBUG_CHANNEL,
    )
    override val channelDetail: Flow<LiveChannelDetail?> = flow {
        val c = repository.fetchChannelList(listOf(id.mapTo())).firstOrNull()
        emit(c?.toLiveChannelDetail())
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
                    Log.e("ChannelViewModel", "fetchChannelSection: error>${cs.title} ", e)
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
                val item = repository.fetchPlaylist(content.item).map { it.toLiveVideoThumbnail() }
                ChannelDetailChannelSection.ChannelDetailContent.MultiPlaylist(item)
            } else {
                val p = repository.fetchPlaylist(content.item)
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
            val item = repository.fetchChannelList(content.item)
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

    override val activities: Flow<List<LiveVideo>> = flow {
        val logs = repository.fetchLiveChannelLogs(id.mapTo(), maxResult = 20)
        val videos = repository.fetchVideoList(logs.map { it.videoId })
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
            .map { it.toLiveVideo() }
        emit(videos)
    }
}

class ChannelDetailDelegateForTwitch @AssistedInject constructor(
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

    override val tabs: Array<ChannelPage> = arrayOf(
        ChannelPage.ABOUT,
        ChannelPage.UPLOADED,
        ChannelPage.DEBUG_CHANNEL,
    )
    override val channelDetail: Flow<LiveChannelDetail?> = flow {
        val u = repository.findUsersById(listOf(id.mapTo()))
        emit(u.map { it.toLiveChannelDetail() }.firstOrNull())
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
    override val activities: Flow<List<LiveVideo>>
        get() = throw AssertionError("unsupported operation")
}

class ChannelDetailChannelSection(
    val id: IdBase,
    val position: Int,
    val title: String,
    val content: ChannelDetailContent<*>?,
) {
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
