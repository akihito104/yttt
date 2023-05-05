package com.freshdigitable.yttt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.ChannelDetailChannelSection.ChannelDetailContent
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: YouTubeLiveRepository,
) : ViewModel() {
    fun fetchChannel(id: LiveChannel.Id): LiveData<LiveChannelDetail?> = flow {
        val channel = repository.fetchChannelList(listOf(id)).firstOrNull()
        emit(channel)
    }.asLiveData(viewModelScope.coroutineContext)

    fun fetchChannelSection(id: LiveChannel.Id): LiveData<List<LiveChannelSection>> = flow {
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

    fun fetchPlaylistItems(
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

    fun fetchActivities(id: LiveChannel.Id): LiveData<List<LiveVideo>> = flow {
        emit(emptyList())
        val logs = repository.fetchLiveChannelLogs(id, maxResult = 20)
        val videos = repository.fetchVideoList(logs.map { it.videoId })
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
        emit(videos)
    }.asLiveData(viewModelScope.coroutineContext)
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

enum class ChannelPage {
    ABOUT, CHANNEL_SECTION, UPLOADED, ACTIVITIES, DEBUG_CHANNEL,
    ;
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
