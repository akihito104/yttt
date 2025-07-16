package com.freshdigitable.yttt.feature.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshdigitable.yttt.compose.AppTheme
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText

internal object YouTubeChannelDetailPageComposableFactory : ChannelDetailPageComposableFactory {
    override fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable {
        when (tab as YouTubeChannelDetailTab) {
            YouTubeChannelDetailTab.About -> return {
                val desc = pagerContent.annotatedDetail.collectAsState(AnnotatableString.empty())
                annotatedText { desc.value }()
            }

            YouTubeChannelDetailTab.Actions -> return {
                val logs = (pagerContent as YouTubeChannelDetailPagerContent)
                    .activities.collectAsState(initial = emptyList())
                list(
                    itemProvider = { logs.value },
                    idProvider = { it.id },
                    content = { videoItem(it.thumbnailUrl, it.text)() },
                )()
            }

            YouTubeChannelDetailTab.Sections -> return {
                val sectionState = (pagerContent as YouTubeChannelDetailPagerContent)
                    .sections.collectAsState(emptyList())
                list(
                    itemProvider = { sectionState.value },
                    idProvider = { it.section.id },
                    content = { cs -> ChannelSectionContent(cs) },
                )()
            }

            YouTubeChannelDetailTab.Uploaded -> return {
                val itemsState = (pagerContent as YouTubeChannelDetailPagerContent)
                    .uploadedVideo.collectAsState(emptyList())
                list(
                    itemProvider = { itemsState.value },
                    idProvider = { it.id },
                    content = { videoItem(it.thumbnailUrl, it.title)() },
                )()
            }

            YouTubeChannelDetailTab.Debug -> return {
                val text = (pagerContent as YouTubeChannelDetailPagerContent).debug
                    .collectAsState(initial = emptyMap())
                list(
                    itemProvider = { text.value.entries.toList() },
                    idProvider = { it.key },
                ) { Text(text = it.value) }()
            }
        }
    }

    private val YouTubeChannelLog.text: String
        get() = "[${type}]$title (${dateTime.toLocalFormattedText(dateTimeSecondFormatter())})"
}

@Composable
private fun ChannelSectionContent(content: ChannelSectionItem) {
    val itemBody: @Composable LazyItemScope.(Int) -> Unit = remember(content) {
        when (content) {
            is SinglePlaylist -> return@remember {
                SinglePlaylistContent(
                    item = content.item[it],
                    modifier = Modifier.fillParentMaxWidth(0.4f),
                )
            }

            is MultipleChannel -> return@remember {
                MultiChannelContent(
                    item = content.item[it],
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }

            is MultiPlaylist -> return@remember {
                MultiPlaylistContent(
                    item = content.item[it],
                    modifier = Modifier.fillParentMaxWidth(0.4f),
                )
            }

            else -> return@remember {}
        }
    }
    Column {
        Text(text = content.title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = { items(count = content.size, itemContent = itemBody) },
        )
    }
}

@Composable
private fun SinglePlaylistContent(item: YouTubePlaylistItemDetail, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ImageLoadableView.Thumbnail(url = item.thumbnailUrl)
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MultiPlaylistContent(item: YouTubePlaylist, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ImageLoadableView.Thumbnail(url = item.thumbnailUrl)
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MultiChannelContent(item: YouTubeChannel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            ImageLoadableView.UserIcon(
                url = item.iconUrl,
                size = 48.dp,
            )
        }
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@LightDarkModePreview
@Composable
private fun ChannelListItemPreview() {
    AppTheme {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(5) {
                MultiChannelContent(
                    item = YouTubeChannelEntity(
                        id = YouTubeChannel.Id("channel_$it"),
                        title = "example_$it channel",
                        iconUrl = "",
                    ),
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }
        }
    }
}
