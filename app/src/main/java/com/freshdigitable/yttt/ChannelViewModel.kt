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
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelDetailEntity
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val twitchRepository: TwitchLiveRepository,
) : ViewModel() {
    fun fetchChannel(id: LiveChannel.Id): LiveData<LiveChannelDetail?> = flow {
        val channel = when (id.platform) {
            LivePlatform.YOUTUBE -> {
                val c = repository.fetchChannelList(listOf(YouTubeChannel.Id(id.value)))
                c.map { it.toLiveChannelDetail() }
            }

            LivePlatform.TWITCH -> {
                val u = twitchRepository.findUsersById(listOf(id.mapTo()))
                u.map { it.toLiveChannelDetail() }
            }
        }.firstOrNull()
        emit(channel)
    }.asLiveData(viewModelScope.coroutineContext)

    fun fetchChannelSection(id: LiveChannel.Id): LiveData<List<YouTubeChannelSection>> = flow {
        if (id.platform != LivePlatform.YOUTUBE) {
            emit(emptyList())
            return@flow
        }
        val channelSection = repository.fetchChannelSection(YouTubeChannel.Id(id.value))
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

    private suspend fun fetchSectionItems(cs: YouTubeChannelSection): ChannelDetailChannelSection {
        val content = cs.content
        val c = if (content is YouTubeChannelSection.Content.Playlist) {
            if (cs.type == YouTubeChannelSection.Type.MULTIPLE_PLAYLIST ||
                cs.type == YouTubeChannelSection.Type.ALL_PLAYLIST
            ) {
                val item = repository.fetchPlaylist(content.item)
                ChannelDetailContent.MultiPlaylist(item)
            } else {
                val p = repository.fetchPlaylist(content.item)
                val item = repository.fetchPlaylistItems(content.item.first())
                return ChannelDetailChannelSection(
                    cs,
                    title = p.first().title,
                    content = ChannelDetailContent.SinglePlaylist(item),
                )
            }
        } else if (content is YouTubeChannelSection.Content.Channels) {
            val item = repository.fetchChannelList(content.item)
            ChannelDetailContent.ChannelList(item.map { it.toLiveChannelDetail() })
        } else {
            ChannelDetailContent.SinglePlaylist(emptyList())
        }
        return ChannelDetailChannelSection(cs, content = c)
    }

    private fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
    ): LiveData<List<YouTubePlaylistItem>> = flow {
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
                        val res = twitchRepository.fetchVideosByUserId(id.mapTo()).map {
                            VideoListItemEntity(
                                id = it.id,
                                thumbnailUrl = it.getThumbnailUrl(),
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
        val logs = repository.fetchLiveChannelLogs(id.mapTo(), maxResult = 20)
        val videos = repository.fetchVideoList(logs.map { it.videoId })
            .map { v -> v to logs.find { v.id == it.videoId } }
            .sortedBy { it.second?.dateTime }
            .map { it.first }
            .map { it.toLiveVideo() }
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
    channelSection: YouTubeChannelSection,
    title: String? = null,
    override val content: ChannelDetailContent<*>?,
) : YouTubeChannelSection by channelSection {
    override val title: String? = title ?: channelSection.title

    sealed class ChannelDetailContent<T> : YouTubeChannelSection.Content<T> {
        data class MultiPlaylist(override val item: List<YouTubePlaylist>) :
            ChannelDetailContent<YouTubePlaylist>()

        data class SinglePlaylist(override val item: List<YouTubePlaylistItem>) :
            ChannelDetailContent<YouTubePlaylistItem>()

        data class ChannelList(override val item: List<LiveChannel>) :
            ChannelDetailContent<LiveChannel>()
    }
}

private fun YouTubeChannelDetail.toLiveChannelDetail(): LiveChannelDetail = LiveChannelDetailEntity(
    id = LiveChannel.Id(id.value, id.platform),
    title = title,
    videoCount = viewsCount,
    isSubscriberHidden = isSubscriberHidden,
    keywords = keywords,
    subscriberCount = subscriberCount,
    uploadedPlayList = uploadedPlayList,
    bannerUrl = bannerUrl,
    customUrl = customUrl,
    description = description,
    viewsCount = viewsCount,
    publishedAt = publishedAt,
    iconUrl = iconUrl,
)

fun YouTubeVideo.toLiveVideo(): LiveVideo = LiveVideoEntity(
    id = LiveVideo.Id(id.value, id.platform),
    title = title,
    channel = channel.toLiveChannel(),
    thumbnailUrl = thumbnailUrl,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    url = url,
)

fun YouTubeChannel.toLiveChannel(): LiveChannel = LiveChannelEntity(
    id = LiveChannel.Id(id.value, id.platform),
    title = title,
    iconUrl = iconUrl,
)
