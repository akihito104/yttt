package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.preview.PreviewLightDarkMode
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.DATE_WEEKDAY_MINUTES
import com.freshdigitable.yttt.data.model.DATE_WEEKDAY_SECONDS
import com.freshdigitable.yttt.data.model.LinkAnnotationDialogState
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.feature.timetable.TimeAdjustment
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.feature.timetable.toAdjustedLocalDateTimeText
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import java.math.BigInteger
import java.time.Instant

@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    topAppBarStateHolder: TopAppBarStateHolder,
    onChannelClick: (LiveChannel.Id) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    val menuItems = viewModel.contextMenuItems.collectAsState(initial = emptyList())
    topAppBarStateHolder.updateMenuItems(menuItems.value)
    val item = viewModel.detail.collectAsState(null)
    val timeAdjustment = viewModel.timeAdjustment.collectAsState()
    VideoDetailScreen(
        videoProvider = { item.value },
        timeAdjustmentProvider = { timeAdjustment.value },
        modifier = modifier,
        thumbnailModifier = thumbnailModifier,
        titleModifier = titleModifier,
        onChannelClick = onChannelClick,
    )
}

@Composable
private fun VideoDetailScreen(
    videoProvider: () -> LiveVideoDetail?,
    timeAdjustmentProvider: () -> TimeAdjustment,
    modifier: Modifier = Modifier,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    onChannelClick: (LiveChannel.Id) -> Unit = {},
) {
    val dialog = remember { LinkAnnotationDialogState() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        val video = videoProvider() ?: return
        ImageLoadableView.Thumbnail(
            url = video.thumbnailUrl,
            contentScale = if (video.isLandscape) ContentScale.FillWidth else ContentScale.FillHeight,
            modifier = Modifier
                .then(thumbnailModifier)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            AnnotatableText(
                annotatableString = video.title,
                fontSize = 18.sp,
                modifier = titleModifier,
                dialog = dialog,
            )
            val timeAdjustment = timeAdjustmentProvider()
            val statsText = video.statsText(timeAdjustment)
            if (statsText.isNotEmpty()) {
                Text(
                    text = statsText,
                    fontSize = 11.sp,
                )
            }
            LiveChannelContentView(
                iconUrl = video.channel.iconUrl,
                title = video.channel.title,
                platformColor = Color(video.channel.platform.color),
                onClick = { onChannelClick(video.channel.id) },
            )
            AnnotatableText(
                annotatableString = video.description,
                fontSize = 14.sp,
                dialog = dialog,
            )
        }
    }
    LinkAnnotationDialog(state = dialog)
}

fun LiveVideoDetail.statsText(timeAdjustment: TimeAdjustment): String {
    val time = when (contentType) {
        TimetablePage.OnAir ->
            "Started:${checkNotNull(dateTime).toAdjustedLocalDateTimeText(timeAdjustment, DATE_WEEKDAY_SECONDS)}"

        TimetablePage.Upcoming ->
            "Starting:${checkNotNull(dateTime).toAdjustedLocalDateTimeText(timeAdjustment, DATE_WEEKDAY_MINUTES)}"

        else -> null
    }
    val count = if (viewerCount != null) "Viewers:$viewerCount" else null
    return listOfNotNull(time, count).joinToString("・")
}

@PreviewLightDarkMode
@Composable
private fun VideoDetailComposePreview() {
    val detail = DetailItem(
        id = LiveVideo.Id("id", YouTubeVideo.Id::class),
        title = AnnotatableString.create("title") { emptyList() },
        channel = LiveChannelEntity(
            id = LiveChannel.Id("channel", YouTubeChannel.Id::class),
            title = "channel",
            iconUrl = "iconUrl",
            platform = YouTube,
        ),
        description = AnnotatableString.create("description\nhttps://example.com") { emptyList() },
        viewerCount = BigInteger.valueOf(100),
        contentType = TimetablePage.Upcoming,
        dateTime = Instant.now(),
    )
    AppTheme {
        VideoDetailScreen(
            videoProvider = { detail },
            timeAdjustmentProvider = { TimeAdjustment.zero() },
        )
    }
}

private data class DetailItem(
    override val id: LiveVideo.Id,
    override val title: AnnotatableString,
    override val thumbnailUrl: String = "",
    override val channel: LiveChannel,
    override val description: AnnotatableString,
    override val viewerCount: BigInteger?,
    override val dateTime: Instant?,
    override val contentType: TimetablePage,
) : LiveVideoDetail
