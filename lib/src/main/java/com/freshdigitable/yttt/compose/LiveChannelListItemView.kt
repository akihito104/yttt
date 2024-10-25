package com.freshdigitable.yttt.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.YouTube

@Composable
fun LiveChannelListItemView(
    modifier: Modifier = Modifier,
    iconUrl: String,
    title: String,
    platformColor: Color? = null,
    iconSize: Dp = 40.dp,
    textOffset: Dp = 16.dp,
    textSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    LiveChannelContentView(
        iconUrl = iconUrl,
        title = title,
        platformColor = platformColor,
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
    modifier: Modifier = Modifier,
    iconUrl: String,
    title: String,
    platformColor: Color? = null,
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
            platformColor = platformColor,
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

@Composable
fun LiveChannelIcon(
    modifier: Modifier = Modifier,
    iconUrl: String,
    platformColor: Color? = null,
    iconSize: Dp,
) {
    Box {
        IconLoadableView(
            modifier = modifier,
            url = iconUrl,
            size = iconSize,
        )
        if (platformColor != null) {
            Box(
                Modifier
                    .background(color = platformColor)
                    .size(iconSize * 0.3f)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun LiveChannelListItemViewPreview() {
    AppTheme {
        LiveChannelListItemView(
            iconUrl = "",
            title = "title",
            platformColor = Color(YouTube.color),
        ) {}
    }
}

@LightDarkModePreview
@Composable
private fun LiveChannelContentViewPreview() {
    AppTheme {
        LiveChannelContentView(iconUrl = "", title = "title", platformColor = Color(YouTube.color))
    }
}
