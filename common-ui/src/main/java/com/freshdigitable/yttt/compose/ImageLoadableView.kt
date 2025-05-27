package com.freshdigitable.yttt.compose

import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ImageLoadableViewDelegateEntryPoint {
    val delegate: ImageLoadableView.Delegate
}

private lateinit var delegateEntryPoint: ImageLoadableViewDelegateEntryPoint

@Composable
private fun requireImageLoadableViewDelegate(): ImageLoadableView.Delegate {
    if (!::delegateEntryPoint.isInitialized) {
        delegateEntryPoint =
            EntryPointAccessors.fromApplication<ImageLoadableViewDelegateEntryPoint>(LocalContext.current)
    }
    return delegateEntryPoint.delegate
}

object ImageLoadableView {
    @Composable
    fun Thumbnail(
        modifier: Modifier = Modifier,
        url: String,
        contentDescription: String? = "",
        contentScale: ContentScale = ContentScale.FillWidth,
        altImage: ImageVector = Icons.Default.PlayArrow,
    ) {
        val m = Modifier
            .then(modifier)
            .aspectRatio(16f / 9f)
        if (url.isEmpty()) {
            VectorThumbnail(
                altImage = altImage,
                modifier = m,
                contentDescription = contentDescription,
            )
        } else {
            DelegateThumbnail(
                url = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = m,
            )
        }
    }

    @Composable
    private fun VectorThumbnail(
        modifier: Modifier = Modifier,
        contentDescription: String? = "",
        altImage: ImageVector,
    ) {
        Image(
            painter = rememberVectorPainter(image = altImage),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }

    @Composable
    private fun DelegateThumbnail(
        modifier: Modifier = Modifier,
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        delegate: Delegate = requireImageLoadableViewDelegate(),
    ) {
        delegate.Thumbnail(modifier, url, contentDescription, contentScale)
    }

    @Composable
    fun UserIcon(
        modifier: Modifier = Modifier,
        url: String,
        contentDescription: String? = "",
        size: Dp,
        altImage: ImageVector = Icons.Default.AccountCircle,
    ) {
        if (url.isEmpty()) {
            VectorUserIcon(
                modifier = Modifier
                    .then(modifier)
                    .size(size),
                altImage = altImage,
                contentDescription = contentDescription,
            )
        } else {
            DelegateUserIcon(
                modifier = Modifier
                    .then(modifier)
                    .size(size)
                    .clip(CircleShape),
                url = url,
                contentDescription = contentDescription,
            )
        }
    }

    @Composable
    private fun VectorUserIcon(
        modifier: Modifier = Modifier,
        altImage: ImageVector,
        contentDescription: String?,
    ) {
        Icon(
            painter = rememberVectorPainter(altImage),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }

    @Composable
    private fun DelegateUserIcon(
        modifier: Modifier = Modifier,
        url: String,
        contentDescription: String?,
        delegate: Delegate = requireImageLoadableViewDelegate(),
    ) {
        delegate.UserIcon(
            modifier = modifier,
            url = url,
            contentDescription = contentDescription,
        )
    }

    @Composable
    fun ChannelArt(
        url: String,
        contentDescription: String? = "",
        delegate: Delegate = requireImageLoadableViewDelegate(),
    ) {
        delegate.ChannelArt(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(32f / 9f),
            url = url,
            contentDescription = contentDescription,
        )
    }

    private val CHANNEL_ART_SAFE_AREA = Size(1235f, 338f)
    fun channelArtCustomCrop(
        input: Bitmap,
        bitmapProvider: ((Size) -> Bitmap)? = null,
    ): Bitmap {
        val scale = input.width / 2048f
        val scaledSize = CHANNEL_ART_SAFE_AREA * scale
        val matrix = Matrix().apply {
            val dx = (scaledSize.width - input.width) / 2f
            val dy = (scaledSize.height - input.height) / 2f
            postTranslate(dx, dy)
        }
        val provider = bitmapProvider ?: { s ->
            createBitmap(s.width.toInt(), s.height.toInt(), input.config ?: Bitmap.Config.ARGB_8888)
        }
        return provider(scaledSize).apply {
            setHasAlpha(input.hasAlpha())
        }.applyCanvas {
            drawBitmap(input, matrix, Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    interface Delegate {
        @Composable
        fun Thumbnail(
            modifier: Modifier,
            url: String,
            contentDescription: String?,
            contentScale: ContentScale,
        )

        @Composable
        fun ChannelArt(
            modifier: Modifier,
            url: String,
            contentDescription: String?,
        )

        @Composable
        fun UserIcon(
            modifier: Modifier,
            url: String,
            contentDescription: String?,
        )
    }
}

typealias ImageLoaderViewSetup = () -> Unit
