package com.freshdigitable.yttt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.ViewModel
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.ChannelDetailChannelSection.ChannelDetailContent
import com.freshdigitable.yttt.compose.VideoListItemEntity
import com.freshdigitable.yttt.data.TwitchLiveRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveChannelDetail
import com.freshdigitable.yttt.data.model.toLiveVideo
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.multibindings.IntoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
) : ViewModel() {
    fun getDelegate(id: LiveChannel.Id): ChannelDetailDelegate =
        checkNotNull(delegateFactory[id.type.java]).create(id)
}

@Module
@InstallIn(ViewModelComponent::class)
interface ChannelDetailDelegateModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeChannel.Id::class)
    fun bindChannelDetailDelegateFactoryYouTube(
        factory: ChannelDetailDelegateForYouTube.Factory,
    ): ChannelDetailDelegate.Factory

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchUser.Id::class)
    fun bindChannelDetailDelegateFactoryTwitch(
        factory: ChannelDetailDelegateForTwitch.Factory,
    ): ChannelDetailDelegate.Factory
}

interface ChannelDetailDelegate {
    val tabs: Array<ChannelPage>
    val channelDetail: Flow<LiveChannelDetail?>
    val uploadedVideo: Flow<List<VideoListItemEntity>>
    val channelSection: Flow<List<YouTubeChannelSection>> // TODO: platform-free
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
    override val uploadedVideo: Flow<List<VideoListItemEntity>> = channelDetail.map { d ->
        val pId = d?.uploadedPlayList ?: return@map emptyList()
        val items = try {
            repository.fetchPlaylistItems(pId)
        } catch (e: Exception) {
            return@map emptyList()
        }
        items.map {
            VideoListItemEntity(
                id = it.id,
                thumbnailUrl = it.thumbnailUrl,
                title = it.title,
            )
        }
    }

    override val channelSection: Flow<List<YouTubeChannelSection>> = flow {
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
    override val uploadedVideo: Flow<List<VideoListItemEntity>> = flow {
        val res = repository.fetchVideosByUserId(id.mapTo()).map {
            VideoListItemEntity(
                id = it.id,
                thumbnailUrl = it.getThumbnailUrl(),
                title = it.title,
            )
        }
        emit(res)
    }
    override val channelSection: Flow<List<YouTubeChannelSection>>
        get() = throw AssertionError("unsupported operation")
    override val activities: Flow<List<LiveVideo>>
        get() = throw AssertionError("unsupported operation")
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
