package com.freshdigitable.yttt.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

abstract class ImageLoadableView {
    @Volatile
    private var _delegate: Delegate? = null
    var delegate: Delegate?
        get() = _delegate
        set(value) {
            synchronized(this) {
                _delegate = value
            }
        }

    companion object : ImageLoadableView() {
        @Composable
        fun Thumbnail(
            modifier: Modifier = Modifier,
            url: String,
            contentDescription: String? = "",
            contentScale: ContentScale = ContentScale.FillWidth,
            altImage: Painter = rememberVectorPainter(image = Icons.Default.PlayArrow),
        ) {
            val d = requireNotNull(delegate)
            d.Thumbnail(modifier, url, contentDescription, contentScale, altImage)
        }

        @Composable
        fun Icon(
            modifier: Modifier = Modifier,
            url: String,
            contentDescription: String? = "",
            size: Dp,
            altImage: Painter = rememberVectorPainter(image = Icons.Default.AccountCircle),
        ) {
            val f = requireNotNull(delegate)
            f.Icon(
                modifier = modifier,
                url = url,
                contentDescription = contentDescription,
                size = size,
                altImage = altImage,
            )
        }

        @Composable
        fun ChannelArt(
            url: String,
            contentDescription: String? = "",
        ) {
            val d = requireNotNull(delegate)
            d.ChannelArt(url = url, contentDescription = contentDescription)
        }
    }

    interface Delegate {
        @Composable
        fun Thumbnail(
            modifier: Modifier,
            url: String,
            contentDescription: String?,
            contentScale: ContentScale,
            altImage: Painter,
        )

        @Composable
        fun ChannelArt(
            url: String,
            contentDescription: String?,
        )

        @Composable
        fun Icon(
            modifier: Modifier,
            url: String,
            contentDescription: String?,
            size: Dp,
            altImage: Painter,
        )
    }
}

typealias ImageLoaderViewSetup = () -> Unit
