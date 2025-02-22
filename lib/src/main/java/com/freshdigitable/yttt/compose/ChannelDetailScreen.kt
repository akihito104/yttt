package com.freshdigitable.yttt.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEntity
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.channel.ChannelDetailChannelSection
import com.freshdigitable.yttt.feature.channel.ChannelPage
import com.freshdigitable.yttt.feature.channel.ChannelViewModel
import java.time.Instant

@Composable
fun ChannelDetailScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val detail = viewModel.channelDetail.collectAsState()
    val dialog = remember { LinkAnnotationDialogState() }
    ChannelDetailScreen(
        pages = viewModel.tabs,
        channelDetail = { detail.value }) { page ->
        when (page) {
            ChannelPage.ABOUT -> {
                val desc = detail.value?.annotatedDescription ?: AnnotatableString.empty()
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    AnnotatableText(
                        fontSize = 14.sp,
                        annotatableString = desc,
                        dialog = dialog,
                    )
                }
            }

            ChannelPage.DEBUG_CHANNEL -> PlainTextPage {
                detail.value?.toString() ?: ""
            }

            ChannelPage.CHANNEL_SECTION -> {
                val sectionState = viewModel.channelSection.collectAsState(emptyList())
                PlainListPage(
                    listProvider = { sectionState.value },
                    idProvider = { it.id },
                    content = { cs -> ChannelSectionContent(cs) },
                )
            }

            ChannelPage.UPLOADED -> {
                val itemsState = viewModel.uploadedVideo.collectAsState(emptyList())
                PlainListPage(
                    listProvider = { itemsState.value },
                    idProvider = { it.id },
                    content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
                )
            }

            ChannelPage.ACTIVITIES -> {
                val logs = viewModel.activities.collectAsState(initial = emptyList())
                PlainListPage(
                    listProvider = { logs.value },
                    idProvider = { it.id },
                    content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
                )
            }
        }
    }
    LinkAnnotationDialog(state = dialog)
}

@Composable
private fun ChannelDetailScreen(
    channelDetail: () -> LiveChannelDetailBody?,
    pages: List<ChannelPage> = ChannelPage.entries,
    pageContent: @Composable (ChannelPage) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ChannelDetailHeader(channelDetail)
        ChannelDetailPager(pages, pageContent)
    }
}

@Composable
private fun ChannelDetailHeader(
    channelDetailProvider: () -> LiveChannelDetailBody?,
) {
    val channelDetail = channelDetailProvider() ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bannerUrl = channelDetail.bannerUrl
        if (bannerUrl?.isNotEmpty() == true) {
            ImageLoadableView.ChannelArt(url = bannerUrl)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            LiveChannelIcon(
                iconUrl = channelDetail.iconUrl,
                iconSize = 56.dp,
            )
            Text(
                text = channelDetail.title,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
            )
            Text(
                text = channelDetail.statsText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ChannelDetailPager(
    pages: List<ChannelPage>,
    pageContent: @Composable (ChannelPage) -> Unit,
) {
    HorizontalPagerWithTabScreen(
        edgePadding = 0.dp,
        tabCount = pages.size,
        tab = { pages[it].name },
    ) {
        pageContent(pages[it])
    }
}

@Composable
private fun PlainTextPage(
    text: @Composable () -> String,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            text = text(),
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun <T> PlainListPage(
    listProvider: () -> List<T>,
    idProvider: (T) -> IdBase,
    content: @Composable (T) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            itemsIndexed(
                items = listProvider(),
                key = { _, item -> idProvider(item).value },
            ) { _, item -> content(item) }
        },
    )
}

@Composable
fun VideoListItem(
    thumbnailUrl: String,
    title: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.5f)
                .aspectRatio(16f / 9f)
                .align(Alignment.Top),
        ) {
            if (thumbnailUrl.isEmpty()) {
                Image(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "",
                    contentScale = ContentScale.FillHeight,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ImageLoadableView.Thumbnail(
                    url = thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ChannelSectionContent(cs: ChannelDetailChannelSection) {
    Column {
        Text(text = cs.title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                val content = cs.content
                items(count = content?.item?.size ?: 0) { i ->
                    when (content) {
                        is ChannelDetailChannelSection.ChannelDetailContent.SinglePlaylist ->
                            SinglePlaylistContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.4f),
                            )

                        is ChannelDetailChannelSection.ChannelDetailContent.ChannelList ->
                            MultiChannelContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.3f),
                            )

                        is ChannelDetailChannelSection.ChannelDetailContent.MultiPlaylist ->
                            MultiPlaylistContent(
                                item = content.item[i],
                                modifier = Modifier.fillParentMaxWidth(0.4f),
                            )

                        else -> {}
                    }
                }
            },
        )
    }
}

@Composable
fun SinglePlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
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
fun MultiPlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
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
fun MultiChannelContent(item: LiveChannel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiveChannelIcon(
            iconUrl = item.iconUrl,
            iconSize = 56.dp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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
fun ChannelScreenPreview() {
    AppTheme {
        ChannelDetailScreen({
            object : LiveChannelDetailBody {
                override val id: LiveChannel.Id get() = YouTubeVideo.Id("a").mapTo()
                override val title: String = "channel title"
                override val statsText: String get() = "@custom_url・Subscribers:52.4k・Videos:132・Views:38,498,283・Published:2021/04/13"
                override val bannerUrl: String? get() = null
                override val iconUrl: String get() = ""
                override val platform: LivePlatform get() = YouTube
                override fun equals(other: Any?): Boolean = throw NotImplementedError()
                override fun hashCode(): Int = throw NotImplementedError()
            }
        }) {
            PlainTextPage { "text here." }
        }
    }
}

@LightDarkModePreview
@Composable
private fun LazyColumnPreview() {
    AppTheme {
        PlainListPage(
            listProvider = {
                listOf("a", "b", "c").mapIndexed { i, title ->
                    object : YouTubePlaylistItem by YouTubePlaylistItemEntity(
                        id = YouTubePlaylistItem.Id(title),
                        playlistId = YouTubePlaylist.Id("d"),
                        title = "title($i)",
                        channel = YouTubeChannelEntity(
                            id = YouTubeChannel.Id("e"),
                            title = "channel title",
                            iconUrl = "",
                        ),
                        videoId = YouTubeVideo.Id("f"),
                        thumbnailUrl = "",
                        description = "description",
                        videoOwnerChannelId = YouTubeChannel.Id("e"),
                        publishedAt = Instant.now(),
                    ) {
                        override fun toString(): String = "id: $id"
                    }
                }
            },
            idProvider = { it.id },
            content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
        )
    }
}
