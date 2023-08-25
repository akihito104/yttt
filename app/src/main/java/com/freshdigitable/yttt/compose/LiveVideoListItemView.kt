package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Top
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
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
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun LiveVideoListItemView(
    video: LiveVideo,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit,
    onMenuClicked: (() -> Unit)? = null,
) {
    if (onMenuClicked == null) {
        LiveVideoListItemView(video = video, modifier = modifier, onItemClick = onItemClick)
    } else {
        Box(modifier = Modifier) {
            LiveVideoListItemView(video = video, modifier = modifier, onItemClick = onItemClick)
            Icon(
                imageVector = Icons.Default.MoreVert,
                modifier = Modifier
                    .size(20.dp)
                    .align(alignment = TopEnd)
                    .clickable(onClick = onMenuClicked),
                contentDescription = "",
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun LiveVideoListItemView(
    video: LiveVideo,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onItemClick),
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
                    imageVector = Icons.Default.PlayArrow,
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
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

private val thumbnailModifier: Modifier = Modifier
    .fillMaxWidth(fraction = 0.55f)
    .aspectRatio(16f / 9f)

private data class ThumbnailKey(val id: String) : Key {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(id.toByteArray(Key.CHARSET))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun LiveVideoListItemViewPreview() {
    AppTheme {
        LiveVideoListItemView(liveVideoSample, onItemClick = {}) {}
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoListItemViewPreviewDark() {
    AppTheme {
        LiveVideoListItemView(liveVideoSample, onItemClick = {}) {}
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun LiveVideoHeaderViewPreview() {
    AppTheme {
        LiveVideoHeaderView(
            label = LocalDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).format(dateWeekdayFormatter)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LiveVideoHeaderViewPreviewDark() {
    AppTheme {
        LiveVideoHeaderView(
            label = LocalDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).format(dateWeekdayFormatter)
        )
    }
}

val liveVideoSample: LiveVideo = LiveVideoEntity(
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
