package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.AnnotatableString.Companion.descriptionUrlAnnotation
import com.freshdigitable.yttt.data.model.LinkAnnotationRange
import com.freshdigitable.yttt.data.model.LinkAnnotationRange.Url.Companion.ellipsize
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoDetailAnnotatedEntity
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.feature.video.VideoDetailViewModel
import java.math.BigInteger

private val baseLinkTextStyle
    @Composable
    get() = SpanStyle(
        color = MaterialTheme.colorScheme.tertiary,
        textDecoration = TextDecoration.Underline,
    )
private val linkStyle
    @Composable
    get() = TextLinkStyles(style = baseLinkTextStyle)

@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel = hiltViewModel(),
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
) {
    val item = viewModel.fetchViewDetail().observeAsState()
    VideoDetailScreen(
        videoProvider = { item.value },
        thumbnailModifier = thumbnailModifier,
        titleModifier = titleModifier,
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VideoDetailScreen(
    videoProvider: () -> LiveVideoDetailAnnotatedEntity?,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
) {
    val dialog = remember { mutableStateOf<LinkAnnotationRange?>(null) }
    val urlHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        val video = videoProvider() ?: return
        val context = LocalContext.current
        GlideImage(
            model = video.thumbnailUrl,
            contentDescription = "",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .then(thumbnailModifier(video.id))
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(bottom = 8.dp)
        ) {
            it.thumbnail(
                Glide.with(context)
                    .load(video.thumbnailUrl)
                    .signature(video.glideSignature)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = video.title,
                fontSize = 18.sp,
                modifier = titleModifier(video.id),
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
                annotatableString = video.annotatableDescription,
                fontSize = 14.sp,
                linkStyle = linkStyle,
            ) {
                check(it is LinkAnnotation.Clickable)
                val linkAnnotation = LinkAnnotationRange.createFromTag(it.tag)
                if (linkAnnotation.needsDialog()) {
                    dialog.value = linkAnnotation
                }
            }
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
fun AnnotatableText(
    fontSize: TextUnit,
    annotatableString: AnnotatableString,
    linkStyle: TextLinkStyles,
    onUrlClicked: (LinkAnnotation) -> Unit,
) {
    if (annotatableString.descriptionAnnotationRangeItems.isEmpty()) {
        Text(
            text = annotatableString.annotatable,
            fontSize = fontSize,
        )
    } else {
        Text(
            text = annotatableString.annotate(linkStyle, onUrlClicked),
            style = TextStyle.Default.copy(
                fontSize = fontSize,
                color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
            ),
        )
    }
}

private fun AnnotatableString.annotate(
    linkStyle: TextLinkStyles,
    onUrlClicked: (LinkAnnotation) -> Unit,
): AnnotatedString {
    val items = descriptionAnnotationRangeItems.map {
        if (it is LinkAnnotationRange.Url && it.text.length > 40) {
            it.ellipsize(totalLength = 40, ellipsis = "...")
        } else {
            it
        }
    }.sortedBy { it.range.first }
    var pos = 0
    return buildAnnotatedString {
        items.forEach { a ->
            if (pos < a.range.first) {
                appendRange(annotatable, pos, a.range.first)
            }
            withLink(a.linkAnnotation(linkStyle, onUrlClicked)) {
                append(a.text)
            }
            pos = a.range.last + 1
        }
        if (pos < annotatable.length) {
            appendRange(annotatable, pos, annotatable.length)
        }
    }
}

private fun LinkAnnotationRange.needsDialog(): Boolean =
    this is LinkAnnotationRange.EllipsizedUrl || this is LinkAnnotationRange.Account

private fun LinkAnnotationRange.linkAnnotation(
    linkStyle: TextLinkStyles,
    onUrlClicked: (LinkAnnotation) -> Unit,
): LinkAnnotation = when {
    this.needsDialog() -> {
        LinkAnnotation.Clickable(
            tag = tag,
            styles = linkStyle,
            linkInteractionListener = onUrlClicked,
        )
    }

    else -> LinkAnnotation.Url(url = url, styles = linkStyle)
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
                                    color = baseLinkTextStyle.color,
                                    textDecoration = baseLinkTextStyle.textDecoration,
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
    val detail = object : LiveVideoDetail,
        LiveVideo by LiveVideoPreviewParamProvider.liveVideo() {
        override val description: String = "description\nhttps://example.com"
        override val viewerCount: BigInteger? = BigInteger.valueOf(100)
    }
    AppTheme {
        VideoDetailScreen(videoProvider = {
            LiveVideoDetailAnnotatedEntity(
                detail = detail,
                annotatableDescription = object : AnnotatableString {
                    override val annotatable: String = detail.description
                    override val descriptionAnnotationRangeItems: List<LinkAnnotationRange> =
                        descriptionUrlAnnotation
                }
            )
        })
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
