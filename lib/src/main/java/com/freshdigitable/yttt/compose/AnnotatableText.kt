package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LinkAnnotationRange
import com.freshdigitable.yttt.data.model.LinkAnnotationRange.Url.Companion.ellipsize

@Composable
fun AnnotatableText(
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    linkStyle: TextLinkStyles = linkStyles,
    annotatableString: AnnotatableString,
    dialog: LinkAnnotationDialogState,
) {
    if (annotatableString.annotationRangeItems.isEmpty()) {
        Text(
            modifier = modifier,
            text = annotatableString.annotatable,
            fontSize = fontSize,
        )
    } else {
        Text(
            modifier = modifier,
            text = annotatableString.annotate(rangeToLink(linkStyle, dialog)),
            style = TextStyle.Default.copy(
                fontSize = fontSize,
                color = LocalContentColor.current.copy(alpha = LocalContentColor.current.alpha),
            ),
        )
    }
}

private fun rangeToLink(
    linkStyle: TextLinkStyles,
    dialog: LinkAnnotationDialogState,
): (LinkAnnotationRange) -> LinkAnnotation = { r ->
    when {
        r.needsDialog() -> {
            LinkAnnotation.Clickable(
                tag = r.tag,
                styles = linkStyle,
                linkInteractionListener = {
                    dialog.showDialog(LinkAnnotationRange.createFromTag(r.tag))
                }
            )
        }

        else -> LinkAnnotation.Url(url = r.url, styles = linkStyle)
    }
}

private fun LinkAnnotationRange.needsDialog(): Boolean =
    this is LinkAnnotationRange.EllipsizedUrl || this is LinkAnnotationRange.Account

@Composable
private fun AnnotatableString.annotate(
    toLink: (LinkAnnotationRange) -> LinkAnnotation,
): AnnotatedString {
    val items = annotationRangeItems.map {
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
            withLink(toLink(a)) {
                append(a.text)
            }
            pos = a.range.last + 1
        }
        if (pos < annotatable.length) {
            appendRange(annotatable, pos, annotatable.length)
        }
    }
}

private val baseLinkTextStyle
    @Composable
    get() = SpanStyle(
        color = MaterialTheme.colorScheme.tertiary,
        textDecoration = TextDecoration.Underline,
    )
val linkStyles
    @Composable
    get() = TextLinkStyles(style = baseLinkTextStyle)

class LinkAnnotationDialogState {
    var currentDialog by mutableStateOf<LinkAnnotationRange?>(null)
        private set

    fun showDialog(dialog: LinkAnnotationRange) {
        currentDialog = dialog
    }

    fun dismiss() {
        currentDialog = null
    }
}

@Composable
fun LinkAnnotationDialog(state: LinkAnnotationDialogState) {
    val urlHandler = LocalUriHandler.current
    when (val d = state.currentDialog) {
        is LinkAnnotationRange.EllipsizedUrl -> EllipsizedUrlConfirmDialog(
            text = d.url,
            onConfirmClicked = {
                urlHandler.openUri(d.url)
                state.dismiss()
            },
            onDismissClicked = { state.dismiss() },
        )

        is LinkAnnotationRange.Account -> AccountDialog(
            account = d.text,
            urls = d.urlCandidate,
            onUrlClicked = {
                urlHandler.openUri(it)
                state.dismiss()
            },
            onDismissRequest = { state.dismiss() },
        )

        null -> {
            // NOP
        }

        else -> throw IllegalStateException("not supported type: $d")
    }
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
private fun AccountDialog(
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
