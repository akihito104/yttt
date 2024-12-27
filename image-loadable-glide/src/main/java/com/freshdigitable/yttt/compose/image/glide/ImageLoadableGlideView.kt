package com.freshdigitable.yttt.compose.image.glide

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import java.security.MessageDigest

internal object ImageLoadableGlideView : ImageLoadableView.Delegate {
    @OptIn(ExperimentalGlideComposeApi::class)
    @Composable
    override fun UserIcon(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
    ) {
        GlideImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }

    @OptIn(ExperimentalGlideComposeApi::class)
    @Composable
    override fun Thumbnail(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
    ) {
        GlideImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }

    @OptIn(ExperimentalGlideComposeApi::class)
    @Composable
    override fun ChannelArt(modifier: Modifier, url: String, contentDescription: String?) {
        GlideImage(
            model = url,
            contentDescription = contentDescription,
            alignment = Alignment.TopCenter,
            modifier = modifier,
            requestBuilderTransform = {
                it.transform(CustomCrop())
            },
        )
    }
}

private class CustomCrop : BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap = ImageLoadableView.channelArtCustomCrop(toTransform) { size ->
        pool.get(size.width.toInt(), size.height.toInt(), toTransform.config)
    }

    override fun equals(other: Any?): Boolean = other is CustomCrop

    override fun hashCode(): Int = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) = messageDigest.update(ID_BYTES)

    companion object {
        private val ID = checkNotNull(CustomCrop::class.java.canonicalName)
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}

internal val setup: ImageLoaderViewSetup = {
    ImageLoadableView.delegate = ImageLoadableGlideView
}
