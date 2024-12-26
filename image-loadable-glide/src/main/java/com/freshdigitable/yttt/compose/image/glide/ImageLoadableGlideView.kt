package com.freshdigitable.yttt.compose.image.glide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.inject.Singleton

internal object ImageLoadableGlideView : ImageLoadableView.Delegate {
    @Composable
    override fun Icon(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        size: Dp,
        altImage: Painter,
    ) {
        IconLoadableView(modifier, url, contentDescription, size, altImage)
    }

    @Composable
    override fun Thumbnail(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        altImage: Painter,
    ) {
        ThumbnailLoadableView(modifier, url, contentDescription, contentScale, altImage)
    }

    @Composable
    override fun ChannelArt(url: String, contentDescription: String?) {
        ChannelArtLoadableView(url = url, contentDescription)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ThumbnailLoadableView(
    modifier: Modifier = Modifier,
    url: String,
    contentDescription: String? = "",
    contentScale: ContentScale = ContentScale.FillWidth,
    altImage: Painter = rememberVectorPainter(image = Icons.Default.PlayArrow),
) {
    val m = Modifier
        .then(modifier)
        .aspectRatio(16f / 9f)
    if (url.isEmpty()) {
        Image(
            painter = altImage,
            contentDescription = contentDescription,
            modifier = m,
        )
    } else {
        GlideImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = m,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun IconLoadableView(
    modifier: Modifier = Modifier,
    url: String,
    contentDescription: String? = "",
    size: Dp,
    altImage: Painter = rememberVectorPainter(image = Icons.Default.AccountCircle),
) {
    if (url.isEmpty()) {
        Icon(
            painter = altImage,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
        )
    } else {
        GlideImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ChannelArtLoadableView(
    url: String,
    contentDescription: String? = "",
) {
    GlideImage(
        model = url,
        contentDescription = contentDescription,
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(32f / 9f),
        requestBuilderTransform = {
            it.transform(CustomCrop(width = 1253, height = 338))
        },
    )
}

private class CustomCrop(
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
        val bitmap = pool.get(w, h, toTransform.config).apply {
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

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageLoaderGlideViewProvider {
    companion object {
        @Provides
        @Singleton
        fun provideSetup(): ImageLoaderViewSetup = {
            ImageLoadableView.delegate = ImageLoadableGlideView
        }
    }
}
