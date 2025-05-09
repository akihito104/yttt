package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Top
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.timetable.TimelineItem
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun LiveVideoListItemView(
    video: TimelineItem,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    onItemClick: () -> Unit,
    onMenuClicked: (() -> Unit)? = null,
) {
    if (onMenuClicked == null) {
        LiveVideoListItemView(
            video = video,
            modifier = modifier,
            thumbnailModifier = thumbnailModifier,
            titleModifier = titleModifier,
            onItemClick = onItemClick,
        )
    } else {
        Box(modifier = Modifier) {
            LiveVideoListItemView(
                video = video,
                modifier = modifier,
                thumbnailModifier = thumbnailModifier,
                titleModifier = titleModifier,
                onItemClick = onItemClick,
            )
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

@Composable
private fun LiveVideoListItemView(
    video: TimelineItem,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
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
            ThumbnailView(video.thumbnail, modifier = thumbnailModifier)
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveChannelContentView(
                    iconUrl = video.channel.iconUrl,
                    title = video.channel.title,
                    platformColor = Color(video.channel.platform.color),
                    textSize = 14.sp,
                    lineHeight = (14 * 1.25).sp,
                )
                Text(
                    text = video.localDateTimeText,
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
                .then(titleModifier)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun RowScope.ThumbnailView(
    video: LiveVideoThumbnail,
    modifier: Modifier = Modifier,
) {
    ImageLoadableView.Thumbnail(
        url = video.thumbnailUrl,
        contentScale = if (video.isLandscape) ContentScale.FillWidth else ContentScale.FillHeight,
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth(fraction = 0.55f)
            .align(Top),
    )
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

@LightDarkModePreview
@Composable
private fun LiveVideoListItemViewPreview(
    @PreviewParameter(LiveVideoPreviewParamProvider::class) video: TimelineItem,
) {
    AppTheme {
        LiveVideoListItemView(video, onItemClick = {}) {}
    }
}


@LightDarkModePreview
@Composable
fun LiveVideoHeaderViewPreview() {
    AppTheme {
        LiveVideoHeaderView(
            label = LocalDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).format(dateWeekdayFormatter)
        )
    }
}

class LiveVideoPreviewParamProvider : PreviewParameterProvider<TimelineItem> {
    override val values: Sequence<TimelineItem> = sequenceOf(
        timelineItem(video = liveVideo()),
        timelineItem(liveVideo(channelTitle = "channel title - チャンネルタイトル")),
    )

    companion object {
        fun timelineItem(video: LiveVideo<*>): TimelineItem = TimelineItem(
            video = video,
            extraHourOfDay = Duration.ZERO,
        )

        fun liveVideo(
            title: String = "video title",
            channelTitle: String = "channel title",
            description: String = "",
            viewerCount: BigInteger? = null,
        ): LiveVideo<*> = LiveVideoEntity(
            title = title,
            scheduledStartDateTime = Instant.now(),
            channel = LiveChannelEntity(
                title = channelTitle,
                iconUrl = "",
                id = YouTubeVideo.Id("b").mapTo(),
                platform = YouTube,
            ),
            id = YouTubeVideo.Id("a").mapTo(),
            thumbnailUrl = "",
            url = "",
            description = description,
            viewerCount = viewerCount,
        )
    }
}

private data class LiveVideoEntity(
    override val id: LiveVideo.Id,
    override val channel: LiveChannel,
    override val title: String,
    override val scheduledStartDateTime: Instant = Instant.EPOCH,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val thumbnailUrl: String,
    override val url: String,
    override val description: String,
    override val viewerCount: BigInteger?,
) : LiveVideo.Upcoming
