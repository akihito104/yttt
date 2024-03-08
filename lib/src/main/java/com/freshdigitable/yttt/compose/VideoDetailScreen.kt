package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.video.LinkAnnotationRange
import com.freshdigitable.yttt.feature.video.LinkAnnotationRange.Url.Companion.ellipsize
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import java.math.BigInteger

private val linkStyle
    @Composable
    get() = SpanStyle(
        color = MaterialTheme.colorScheme.tertiary,
        textDecoration = TextDecoration.Underline
    )

@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val item = viewModel.fetchViewDetail().observeAsState()
    VideoDetailScreen(videoProvider = { item.value })
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VideoDetailScreen(videoProvider: () -> LiveVideoDetailAnnotated?) {
    val dialog = remember { mutableStateOf<LinkAnnotationRange?>(null) }
    val urlHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        val video = videoProvider() ?: return
        GlideImage(
            model = video.thumbnailUrl,
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
                text = video.title,
                fontSize = 18.sp,
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
            if (video.descriptionAnnotationRangeItems.isEmpty()) {
                Text(
                    text = video.description,
                    fontSize = 14.sp,
                )
            } else {
                DescriptionText(
                    fontSize = 14.sp,
                    annotatedDescription = video.annotatedDescription(linkStyle),
                    onUrlClicked = {
                        val linkAnnotation = LinkAnnotationRange.createFromTag(it.tag)
                        if (linkAnnotation is LinkAnnotationRange.EllipsizedUrl ||
                            linkAnnotation is LinkAnnotationRange.Account
                        ) {
                            dialog.value = linkAnnotation
                        } else {
                            urlHandler.openUri(linkAnnotation.url)
                        }
                    },
                )
            }
            Text(
                text = video.toString(),
                fontSize = 14.sp,
            )
        }
    }
    when (val d = dialog.value) {
        is LinkAnnotationRange.EllipsizedUrl -> EllipsizedUrlConfirmDialog(
            text = d.url,
            onConfirmClicked = {
                urlHandler.openUri(d.url)
                dialog.value = null
            },
            onDismissClicked = { dialog.value = null },
        )

        is LinkAnnotationRange.Account -> AccountDialog(
            account = d.text,
            urls = d.urlCandidate,
            onUrlClicked = {
                urlHandler.openUri(it)
                dialog.value = null
            },
            onDismissRequest = { dialog.value = null },
        )

        null -> {
            // NOP
        }

        else -> throw IllegalStateException("not supported type: $d")
    }
}

@Composable
private fun DescriptionText(
    fontSize: TextUnit,
    annotatedDescription: AnnotatedString,
    onUrlClicked: (AnnotatedString.Range<String>) -> Unit,
) {
    ClickableText(
        style = TextStyle.Default.copy(
            fontSize = fontSize,
            color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
        ),
        text = annotatedDescription,
        onClick = { pos ->
            val annotation = annotatedDescription.getStringAnnotations(start = pos, end = pos)
                .firstOrNull() ?: return@ClickableText
            onUrlClicked(annotation)
        },
    )
}

private fun LiveVideoDetailAnnotated.annotatedDescription(
    linkStyle: SpanStyle
): AnnotatedString {
    var pos = 0
    val items = descriptionAnnotationRangeItems.map {
        if (it is LinkAnnotationRange.Url && it.text.length > 40) {
            it.ellipsize(totalLength = 40, ellipsis = "...")
        } else {
            it
        }
    }.sortedBy { it.range.first }
    return buildAnnotatedString {
        items.forEach { a ->
            if (pos < a.range.first) {
                appendRange(description, pos, a.range.first)
            }
            annotateUrl(a.tag, a.url, a.text, linkStyle)
            pos = a.range.last + 1
        }
        if (pos < description.length) {
            appendRange(description, pos, description.length)
        }
    }
}

private fun AnnotatedString.Builder.annotateUrl(
    tag: String,
    url: String,
    text: String = url,
    spanStyle: SpanStyle,
) {
    pushStringAnnotation(tag = tag, annotation = url)
    withStyle(spanStyle) {
        append(text)
    }
    pop()
}

@Composable
private fun EllipsizedUrlConfirmDialog(
    text: String,
    onConfirmClicked: () -> Unit,
    onDismissClicked: () -> Unit,
    onDismissRequest: () -> Unit = onDismissClicked,
) {
    AlertDialog(
        text = { Text(text = text) },
        confirmButton = {
            Button(onClick = onConfirmClicked) {
                Text(text = "go to website")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissClicked) {
                Text(text = "dismiss")
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun AccountDialog(
    account: String,
    urls: List<String>,
    onUrlClicked: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmClicked: () -> Unit = onDismissRequest,
) {
    AlertDialog(
        title = { Text(text = "Choose URL for $account") },
        text = {
            Column {
                urls.forEach {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = it,
                                style = TextStyle.Default.copy(
                                    color = linkStyle.color,
                                    textDecoration = linkStyle.textDecoration,
                                ),
                            )
                        },
                        modifier = Modifier.clickable { onUrlClicked(it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmClicked) {
                Text(text = "dismiss")
            }
        },
        onDismissRequest = onDismissRequest,
    )
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

@LightModePreview
@Composable
fun VideoDetailComposePreview() {
    AppTheme {
        VideoDetailScreen(videoProvider = {
            object : LiveVideoDetailAnnotated,
                LiveVideo by LiveVideoPreviewParamProvider.liveVideo() {
                override val description: String = "description"
                override val viewerCount: BigInteger? = BigInteger.valueOf(100)
                override val descriptionAnnotationRangeItems: List<LinkAnnotationRange> =
                    emptyList()

                override fun toString(): String = "debug json text"
            }
        })
    }
}

@LightDarkModePreview
@Composable
fun DescriptionTextPreview() {
    AppTheme {
        DescriptionText(
            fontSize = 14.sp,
            annotatedDescription = buildAnnotatedString {
                appendLine("hello.")
                annotateUrl(tag = "URL", url = "http://example.com/", spanStyle = linkStyle)
            },
            onUrlClicked = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun EllipsizedUrlConfirmDialogPreview() {
    AppTheme {
        EllipsizedUrlConfirmDialog(
            text = "https://www.example.com/veryverylongurl",
            onConfirmClicked = { },
            onDismissClicked = { },
        )
    }
}

@LightDarkModePreview
@Composable
private fun AccountDialogPreview() {
    AppTheme {
        AccountDialog(
            account = "@account01",
            urls = listOf("https://example.com/1/@account01", "https://example.com/2/@account01"),
            onUrlClicked = {},
            onDismissRequest = {},
        )
    }
}
