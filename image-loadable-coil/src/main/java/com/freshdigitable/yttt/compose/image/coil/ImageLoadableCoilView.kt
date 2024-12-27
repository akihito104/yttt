package com.freshdigitable.yttt.compose.image.coil

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import okhttp3.OkHttpClient

internal fun setup(okHttpClient: OkHttpClient): ImageLoaderViewSetup = {
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

internal object ImageLoadableCoilView : ImageLoadableView.Delegate {
    @Composable
    override fun Thumbnail(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
    ) {
        require(url.isNotEmpty())
        AsyncImage(
            modifier = modifier,
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
        )
    }

    @Composable
    override fun ChannelArt(modifier: Modifier, url: String, contentDescription: String?) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .transformations(ChannelArtCustomCrop())
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = modifier,
        )
    }

    @Composable
    override fun UserIcon(
        modifier: Modifier,
        url: String,
        contentDescription: String?,
    ) {
        require(url.isNotEmpty())
        AsyncImage(
            modifier = modifier,
            model = url,
            contentDescription = contentDescription,
        )
    }
}

private class ChannelArtCustomCrop : Transformation() {
    override val cacheKey: String = "${this::class.qualifiedName}"

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap =
        ImageLoadableView.channelArtCustomCrop(input)
}
