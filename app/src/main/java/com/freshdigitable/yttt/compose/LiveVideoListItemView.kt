package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Top
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.Key
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.google.accompanist.themeadapter.material.MdcTheme
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LiveVideoListItemView(
    video: LiveVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            if (video.thumbnailUrl.isNotEmpty()) {
                GlideImage(
                    model = video.thumbnailUrl,
                    contentDescription = "",
                    modifier = thumbnailModifier.align(Top)
                ) {
                    it.signature(ThumbnailKey(video.id.value))
                }
            } else {
                Image(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "",
                    modifier = thumbnailModifier.align(Top),
                )
            }
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveChannelContentView(
                    iconUrl = video.channel.iconUrl,
                    title = video.channel.title,
                )
                Text(
                    text = video.scheduledStartDateTime?.toLocalFormattedText(dateTimeFormatter)
                        ?: "",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
        }
        Text(
            text = video.title,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp)
        )
    }
}

@Composable
fun LiveVideoHeaderView(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Cyan)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label)
    }
}

private val thumbnailModifier: Modifier = Modifier
    .fillMaxWidth(fraction = 0.55f)
    .aspectRatio(16f / 9f)

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun LiveVideoListItemViewPreview() {
    MdcTheme {
        LiveVideoListItemView(
            LiveVideoEntity(
                title = "video title",
                scheduledStartDateTime = Instant.now(),
                channel = LiveChannelEntity(
                    title = "channel title",
                    iconUrl = "",
                    id = LiveChannel.Id("b")
                ),
                id = LiveVideo.Id("a"),
                thumbnailUrl = "",
            )
        ) {}
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun LiveVideoHeaderViewPreview() {
    MdcTheme {
        LiveVideoHeaderView(
            label = LocalDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).format(dateWeekdayFormatter)
        )
    }
}

private data class ThumbnailKey(val id: String) : Key {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(id.toByteArray(Key.CHARSET))
    }
}
