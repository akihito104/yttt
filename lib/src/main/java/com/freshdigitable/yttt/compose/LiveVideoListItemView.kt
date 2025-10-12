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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.preview.PreviewLightDarkMode
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.timetable.TimeAdjustment
import com.freshdigitable.yttt.feature.timetable.TimelineItem
import com.freshdigitable.yttt.lib.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun LiveVideoListItemView(
    video: TimelineItem,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
) {
    if (onMenuClick == null) {
        LiveVideoListItemView(
            video = video,
            modifier = modifier,
            thumbnailModifier = thumbnailModifier,
            titleModifier = titleModifier,
            onItemClick = onItemClick,
        )
    } else {
        Box(modifier = modifier) {
            LiveVideoListItemView(
                video = video,
                thumbnailModifier = thumbnailModifier,
                titleModifier = titleModifier,
                onItemClick = onItemClick,
            )
            Icon(
                imageVector = Icons.Default.MoreVert,
                modifier = Modifier
                    .size(20.dp)
                    .align(alignment = TopEnd)
                    .clickable(onClick = onMenuClick),
                contentDescription = "",
            )
        }
    }
}

@Composable
private fun LiveVideoListItemView(
    video: TimelineItem,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
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
                .wrapContentHeight(),
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
                        .wrapContentHeight(),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 4.dp),
        ) {
            if (video.isPinned == true) {
                Icon(
                    painter = painterResource(R.drawable.baseline_push_pin_24),
                    contentDescription = "pinned item",
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp)
                        .size(16.dp),
                )
            }
            Text(
                text = video.title,
                fontSize = 18.sp,
                modifier = titleModifier
                    .padding(start = 4.dp),
            )
        }
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
fun LiveVideoHeaderView(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
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

@PreviewLightDarkMode
@Composable
private fun LiveVideoListItemViewPreview(
    @PreviewParameter(LiveVideoPreviewParamProvider::class) video: TimelineItem,
) {
    AppTheme {
        LiveVideoListItemView(video, onItemClick = {}) {}
    }
}

@PreviewLightDarkMode
@Composable
private fun LiveVideoHeaderViewPreview() {
    AppTheme {
        LiveVideoHeaderView(
            label = LocalDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).format(dateWeekdayFormatter),
        )
    }
}

class LiveVideoPreviewParamProvider : PreviewParameterProvider<TimelineItem> {
    override val values: Sequence<TimelineItem> = sequenceOf(
        timelineItem(video = upcomingVideo()),
        timelineItem(
            freeChat(
                title = "予定表兼フリーチャット - this is free chat space",
                channelTitle = "channel title - チャンネルタイトル",
                isPinned = true,
            ),
        ),
    )

    companion object {
        fun timelineItem(video: LiveTimelineItem): TimelineItem = TimelineItem.Simple(
            video = video,
            timeAdjustment = TimeAdjustment(Duration.ZERO),
        )

        fun upcomingVideo(
            title: String = "video title",
            channelTitle: String = "channel title",
        ): LiveTimelineItem = LiveVideoUpcomingEntity(
            title = title,
            dateTime = Instant.now(),
            channel = LiveChannelEntity(
                title = channelTitle,
                iconUrl = "",
                id = YouTubeChannel.Id("b").mapTo(),
                platform = YouTube,
            ),
            id = YouTubeVideo.Id("a").mapTo(),
        )

        fun freeChat(
            title: String = "video title",
            channelTitle: String = "channel title",
            isPinned: Boolean = false,
        ): LiveTimelineItem = LiveVideoUpcomingEntity(
            title = title,
            dateTime = Instant.now(),
            channel = LiveChannelEntity(
                title = channelTitle,
                iconUrl = "",
                id = YouTubeChannel.Id("b").mapTo(),
                platform = YouTube,
            ),
            id = YouTubeVideo.Id("a").mapTo(),
            isPinned = isPinned,
        )
    }

    private data class LiveVideoUpcomingEntity(
        override val id: LiveVideo.Id,
        override val channel: LiveChannel,
        override val title: String,
        override val dateTime: Instant = Instant.EPOCH,
        override val thumbnailUrl: String = "",
        override val isPinned: Boolean? = null,
    ) : LiveTimelineItem
}
