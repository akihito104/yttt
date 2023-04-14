package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.VideoDetailViewModel
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.google.accompanist.themeadapter.material.MdcTheme
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    id: LiveVideo.Id,
) {
    val item = viewModel.fetchViewDetail(id).observeAsState().value ?: return
    VideoDetailScreen(video = item)
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VideoDetailScreen(video: LiveVideo) {
    val statsText = video.statsText
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        GlideImage(
            model = video.thumbnailUrl,
            contentDescription = "",
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .padding(bottom = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = video.title,
                fontSize = 18.sp,
            )
            if (statsText.isNotEmpty()) {
                Text(
                    text = statsText,
                    fontSize = 11.sp,
                )
            }
            LiveChannelContentView(
                iconUrl = video.channel.iconUrl,
                title = video.channel.title,
            )
            if (video is LiveVideoDetail) {
                Text(
                    text = video.description,
                    fontSize = 14.sp,
                )
            }
            Text(
                text = video.toString(),
                fontSize = 14.sp,
            )
        }
    }
}

private val LiveVideo.statsText: String
    get() {
        val time = if (isNowOnAir()) {
            "Started:${requireNotNull(actualStartDateTime).toLocalFormattedText(startedFormat)}"
        } else if (isUpcoming()) {
            "Starting:${requireNotNull(scheduledStartDateTime).toLocalFormattedText(startingFormat)}"
        } else null
        val count =
            if ((this as? LiveVideoDetail)?.viewerCount != null) "Viewers:${viewerCount.toString()}"
            else null
        return listOfNotNull(time, count).joinToString("・")
    }

private const val startedFormat = "yyyy/MM/dd(E) HH:mm:ss"
private const val startingFormat = "yyyy/MM/dd(E) HH:mm"
private fun Instant.toLocalFormattedText(format: String): String {
    val dateTimeFormatter = DateTimeFormatter.ofPattern(format)
    val localDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
    return localDateTime.format(dateTimeFormatter)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun VideoDetailComposePreview() {
    MdcTheme {
        VideoDetailScreen(video = object : LiveVideoDetail, LiveVideo by LiveVideoEntity(
            id = LiveVideo.Id("a"),
            title = "video title",
            channel = LiveChannelEntity(
                id = LiveChannel.Id("b"),
                title = "channel title",
                iconUrl = "",
            ),
            scheduledStartDateTime = Instant.now(),
            thumbnailUrl = "",
        ) {
            override val description: String = "description"
            override val viewerCount: BigInteger? = BigInteger.valueOf(100)

            override fun toString(): String = "debug json text"
        })
    }
}
