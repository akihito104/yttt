package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview

@Composable
fun LiveChannelListItemView(
    iconUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
    textOffset: Dp = 16.dp,
    textSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    LiveChannelContentView(
        iconUrl = iconUrl,
        title = title,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .padding(start = 16.dp, end = 24.dp),
        iconSize = iconSize,
        textOffset = textOffset,
        textSize = textSize,
    )
}

@Composable
fun LiveChannelContentView(
    iconUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    textOffset: Dp = 8.dp,
    textSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        LiveChannelIcon(
            iconUrl = iconUrl,
            iconSize = iconSize,
            modifier = Modifier.align(Alignment.Top),
        )
        Spacer(modifier = Modifier.size(textOffset))
        Text(
            text = title,
            fontSize = textSize,
            modifier = Modifier.align(Alignment.CenterVertically),
            lineHeight = lineHeight,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LiveChannelIcon(
    iconUrl: String,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    if (iconUrl.isEmpty()) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "",
            modifier = modifier
                .size(iconSize)
        )
    } else {
        GlideImage(
            model = iconUrl,
            contentDescription = "",
            modifier = modifier
                .size(iconSize)
                .clip(CircleShape),
        )
    }
}

@LightDarkModePreview
@Composable
private fun LiveChannelListItemViewPreview() {
    AppTheme {
        LiveChannelListItemView("", "title") {}
    }
}

@LightDarkModePreview
@Composable
private fun LiveChannelContentViewPreview() {
    AppTheme {
        LiveChannelContentView("", "title")
    }
}
