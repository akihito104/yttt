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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoForDetail
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import java.math.BigInteger

@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel = hiltViewModel(),
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    val item = viewModel.fetchViewDetail().observeAsState()
    VideoDetailScreen(
        videoProvider = { item.value },
        thumbnailModifier = thumbnailModifier,
        titleModifier = titleModifier,
    )
}

@Composable
private fun VideoDetailScreen(
    videoProvider: () -> LiveVideoForDetail?,
    thumbnailModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    val dialog = remember { LinkAnnotationDialogState() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        val entity = videoProvider() ?: return
        val video = entity.video
        ImageLoadableView.Thumbnail(
            url = video.thumbnailUrl,
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
                annotatableString = entity.annotatableTitle,
                fontSize = 18.sp,
                modifier = titleModifier,
                dialog = dialog,
            )
            val statsText = video.statsText
            if (statsText.isNotEmpty()) {
                Text(
                    text = statsText,
                    fontSize = 11.sp,
                )
            }
            LiveChannelContentView(
                iconUrl = video.channel.iconUrl,
                title = video.channel.title,
                platformColor = Color(video.channel.platform.color)
            )
            AnnotatableText(
                annotatableString = entity.annotatableDescription,
                fontSize = 14.sp,
                dialog = dialog,
            )
        }
    }
    LinkAnnotationDialog(state = dialog)
}

private val LiveVideo<*>.statsText: String
    get() {
        val time = when (this) {
            is LiveVideo.OnAir ->
                "Started:${actualStartDateTime.toLocalFormattedText(dateTimeSecondFormatter)}"

            is LiveVideo.Upcoming ->
                "Starting:${scheduledStartDateTime.toLocalFormattedText(dateTimeFormatter)}"

            else -> null
        }
        val count = if (viewerCount != null) "Viewers:${viewerCount.toString()}" else null
        return listOfNotNull(time, count).joinToString("・")
    }

@LightDarkModePreview
@Composable
fun VideoDetailComposePreview() {
    val detail = LiveVideoPreviewParamProvider.liveVideo(
        description = "description\nhttps://example.com",
        viewerCount = BigInteger.valueOf(100),
    )
    AppTheme {
        VideoDetailScreen(videoProvider = {
            LiveVideoDetailAnnotatedEntity(
                video = detail,
                annotatableDescription = AnnotatableString.create(detail.description) { emptyList() },
                annotatableTitle = AnnotatableString.empty()
            )
        })
    }
}

private data class LiveVideoDetailAnnotatedEntity(
    override val video: LiveVideo<*>,
    override val annotatableDescription: AnnotatableString,
    override val annotatableTitle: AnnotatableString,
) : LiveVideoForDetail
