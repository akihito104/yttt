package com.freshdigitable.yttt.feature.channel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.compose.MainNavRoute
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ChannelDetailDelegate {
    private val channelId = MainNavRoute.ChannelDetail.getChannelId(savedStateHandle)
    private val delegate = checkNotNull(delegateFactory[channelId.type.java]).create(channelId)

    override val tabs: Array<ChannelPage> get() = delegate.tabs
    override val channelDetail: Flow<LiveChannelDetail?> get() = delegate.channelDetail
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> get() = delegate.uploadedVideo
    override val channelSection: Flow<List<ChannelDetailChannelSection>> get() = delegate.channelSection
    override val activities: Flow<List<LiveVideo>> get() = delegate.activities
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
