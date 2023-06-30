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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.VideoDetailViewModel
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.google.accompanist.themeadapter.material.MdcTheme
import java.math.BigInteger
import java.time.Instant


@Composable
fun VideoDetailScreen(
    id: LiveVideo.Id,
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val item = viewModel.fetchViewDetail(id).observeAsState()
    VideoDetailScreen(videoProvider = { item.value })
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VideoDetailScreen(videoProvider: () -> LiveVideo?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        val video = videoProvider()
        GlideImage(
            model = video?.thumbnailUrl ?: "",
            contentDescription = "",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(bottom = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = video?.title ?: "",
                fontSize = 18.sp,
            )
            val statsText = video?.statsText ?: ""
            if (statsText.isNotEmpty()) {
                Text(
                    text = statsText,
                    fontSize = 11.sp,
                )
            }
            LiveChannelContentView(
                iconUrl = video?.channel?.iconUrl ?: "",
                title = video?.channel?.title ?: "",
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
            "Started:${
                requireNotNull(actualStartDateTime).toLocalFormattedText(dateTimeSecondFormatter)
            }"
        } else if (isUpcoming()) {
            "Starting:${
                requireNotNull(scheduledStartDateTime).toLocalFormattedText(dateTimeFormatter)
            }"
        } else null
        val count =
            if ((this as? LiveVideoDetail)?.viewerCount != null) "Viewers:${viewerCount.toString()}"
            else null
        return listOfNotNull(time, count).joinToString("・")
    }

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun VideoDetailComposePreview() {
    MdcTheme {
        VideoDetailScreen(videoProvider = {
            object : LiveVideoDetail, LiveVideo by LiveVideoEntity(
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
            }
        })
    }
}
