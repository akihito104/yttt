package com.freshdigitable.yttt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.ChannelDetailChannelSection.ChannelDetailContent
import com.freshdigitable.yttt.compose.VideoListItemEntity
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.TwitchLiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
    private val twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    fun fetchChannel(id: LiveChannel.Id): LiveData<LiveChannelDetail?> = flow {
        val channel = when (id.platform) {
            LivePlatform.YOUTUBE -> repository.fetchChannelList(listOf(id))
            LivePlatform.TWITCH -> twitchRepository.findUsersById(listOf(id))
        }.firstOrNull()
        emit(channel)
    }.asLiveData(viewModelScope.coroutineContext)

    fun fetchChannelSection(id: LiveChannel.Id): LiveData<List<LiveChannelSection>> = flow {
        if (id.platform != LivePlatform.YOUTUBE) {
            emit(emptyList())
            return@flow
        }
        val channelSection = repository.fetchChannelSection(id)
            .mapNotNull { cs ->
                try {
                    fetchSectionItems(cs)
                } catch (e: IOException) {
                    Log.e("ChannelViewModel", "fetchChannelSection: error>${cs.title} ", e)
                    null
                }
            }
            .sortedBy { it.position }
        emit(channelSection)
    }.asLiveData(viewModelScope.coroutineContext)

    private suspend fun fetchSectionItems(cs: LiveChannelSection): ChannelDetailChannelSection {
        val content = cs.content
        val c = if (content is LiveChannelSection.Content.Playlist) {
            if (cs.type == LiveChannelSection.Type.MULTIPLE_PLAYLIST ||
                cs.type == LiveChannelSection.Type.ALL_PLAYLIST
            ) {
                val item = repository.fetchPlaylist(content.item)
                ChannelDetailContent.MultiPlaylist(item)
            } else {
                val p = repository.fetchPlaylist(content.item)
                val item = repository.fetchPlaylistItems(content.item.first())
                return ChannelDetailChannelSection(
                    cs,
                    _title = p.first().title,
                    content = ChannelDetailContent.SinglePlaylist(item),
                )
            }
        } else if (content is LiveChannelSection.Content.Channels) {
            val item = repository.fetchChannelList(content.item)
            ChannelDetailContent.ChannelList(item)
        } else {
            ChannelDetailContent.SinglePlaylist(emptyList())
        }
        return ChannelDetailChannelSection(cs, content = c)
    }

    private fun fetchPlaylistItems(
        id: LivePlaylist.Id,
    ): LiveData<List<LivePlaylistItem>> = flow {
        emit(emptyList())
        val items = try {
            repository.fetchPlaylistItems(id)
        } catch (e: Exception) {
            emptyList()
        }
        emit(items)
    }.asLiveData(viewModelScope.coroutineContext)

    fun fetchVideoListItems(
        detail: LiveData<LiveChannelDetail?>,
    ): LiveData<List<VideoListItemEntity>> {
        return detail.switchMap { d ->
            val id = d?.id ?: return@switchMap emptyState
            when (id.platform) {
                LivePlatform.YOUTUBE -> {
                    val pId = d.uploadedPlayList ?: return@switchMap emptyState
                    fetchPlaylistItems(pId).map { items ->
                        items.map {
                            VideoListItemEntity(
                                id = it.id,
                                thumbnailUrl = it.thumbnailUrl,
                                title = it.title
                            )
                        }
                    }
                }

                LivePlatform.TWITCH -> {
                    liveData {
                        val res = twitchRepository.fetchVideosByChannelId(id).map {
                            VideoListItemEntity(
                                id = it.id,
                                thumbnailUrl = it.thumbnailUrl,
                                title = it.title
                            )
                        }
                        emit(res)
                    }
                }
            }
        }
    }

    fun fetchActivities(id: LiveChannel.Id): LiveData<List<LiveVideo>> = flow {
        emit(emptyList())
        if (id.platform != LivePlatform.YOUTUBE) {
            return@flow
        }
        val logs = repository.fetchLiveChannelLogs(id, maxResult = 20)
        val videos = repository.fetchVideoList(logs.map { it.videoId })
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
        emit(videos)
    }.asLiveData(viewModelScope.coroutineContext)

    companion object {
        private val emptyState = MutableLiveData(emptyList<VideoListItemEntity>())
    }
}

class CustomCrop(
    private val width: Int,
    private val height: Int
) : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val scaled = toTransform.width / 2048.0f
        val w = (width * scaled).toInt()
        val h = (height * scaled).toInt()
        val dx = (w - toTransform.width) * 0.5f
        val dy = (h - toTransform.height) * 0.5f
        val matrix = Matrix().apply {
            postTranslate(dx, dy)
        }
        val bitmap = pool.get(w, h, toTransform.config ?: Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(toTransform.hasAlpha())
        }
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(toTransform, matrix, Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.setBitmap(null)
        return bitmap
    }

    override fun equals(other: Any?): Boolean = other is CustomCrop

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) = messageDigest.update(ID_BYTES)

    companion object {
        private val ID = checkNotNull(CustomCrop::class.java.canonicalName)
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}

enum class ChannelPage(val platform: Array<LivePlatform> = LivePlatform.values()) {
    ABOUT, CHANNEL_SECTION(arrayOf(LivePlatform.YOUTUBE)), UPLOADED,
    ACTIVITIES(arrayOf(LivePlatform.YOUTUBE)), DEBUG_CHANNEL,
    ;

    companion object {
        fun findByPlatform(platform: LivePlatform): Array<ChannelPage> {
            return ChannelPage.values().filter { p -> p.platform.any { it == platform } }
                .toTypedArray()
        }
    }
}

class ChannelDetailChannelSection(
    channelSection: LiveChannelSection,
    _title: String? = null,
    override val content: ChannelDetailContent<*>?,
) : LiveChannelSection by channelSection {
    override val title: String? = _title ?: channelSection.title

    sealed class ChannelDetailContent<T> : LiveChannelSection.Content<T> {
        data class MultiPlaylist(override val item: List<LivePlaylist>) :
            ChannelDetailContent<LivePlaylist>()

        data class SinglePlaylist(override val item: List<LivePlaylistItem>) :
            ChannelDetailContent<LivePlaylistItem>()

        data class ChannelList(override val item: List<LiveChannel>) :
            ChannelDetailContent<LiveChannel>()
    }
}
