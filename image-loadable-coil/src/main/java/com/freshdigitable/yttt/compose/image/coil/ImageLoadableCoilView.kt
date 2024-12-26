package com.freshdigitable.yttt.compose.image.coil

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.transform.Transformation
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

internal object ImageLoadableCoilView : ImageLoadableView.Delegate {
    @Composable
    override fun Thumbnail(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        altImage: Painter
    ) {
        AsyncImage(
            modifier = Modifier
                .then(modifier)
                .aspectRatio(16f / 9f),
            model = url.ifEmpty { null },
            contentDescription = contentDescription,
            contentScale = contentScale,
            fallback = altImage,
        )
    }

    @Composable
    override fun ChannelArt(url: String, contentDescription: String?) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .transformations(ChannelArtCustomCrop())
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(32f / 9f)
        )
    }

    @Composable
    override fun Icon(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        size: Dp,
        altImage: Painter
    ) {
        AsyncImage(
            modifier = Modifier
                .then(modifier)
                .size(size)
                .clip(CircleShape),
            model = url.ifEmpty { null },
            contentDescription = contentDescription,
            fallback = altImage,
        )
    }
}

private class ChannelArtCustomCrop : Transformation() {
    override val cacheKey: String = "${this::class.qualifiedName}"

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        val scale = input.width / 2048f
        val w = (CHANNEL_ART_SAFE_AREA.width * scale).toInt()
        val h = (CHANNEL_ART_SAFE_AREA.height * scale).toInt()
        val matrix = Matrix().apply {
            val dx = (w - input.width) / 2f
            val dy = (h - input.height) / 2f
            postTranslate(dx, dy)
        }
        val output = createBitmap(w, h, input.config)
        return output.applyCanvas {
            drawBitmap(input, matrix, Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    companion object {
        private val CHANNEL_ART_SAFE_AREA = Size(1235f, 338f)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageLoadableCoilViewProvider {
    companion object {
        @Provides
        @Singleton
        fun provideSetup(okHttpClient: OkHttpClient): ImageLoaderViewSetup = {
            ImageLoadableView.delegate = ImageLoadableCoilView
            SingletonImageLoader.setSafe { context ->
                ImageLoader.Builder(context)
                    .diskCache { null }
                    .components {
                        add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                    }
                    .crossfade(true)
                    .build()
            }
        }
    }
}
