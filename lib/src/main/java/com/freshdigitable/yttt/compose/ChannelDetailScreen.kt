package com.freshdigitable.yttt.compose

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
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
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
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelPage
import com.freshdigitable.yttt.feature.channel.ChannelViewModel
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

@Composable
fun ChannelDetailScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    AppLogger.logD("ChannelDetail") { "start:" }
    val detail = viewModel.channelDetailBody.collectAsState()
    val dialog = remember { LinkAnnotationDialogState() }
    val scope = remember(viewModel, dialog) { ChannelDetailPageScope.create(viewModel, dialog) }
    ChannelDetailScreen(
        channelDetail = { detail.value },
        tabs = viewModel.tabs,
        pages = pages,
        pageScope = scope,
    )
    LinkAnnotationDialog(state = dialog)
}

@Composable
private fun ChannelDetailScreen(
    channelDetail: () -> LiveChannelDetailBody?,
    tabs: List<ChannelPage> = ChannelPage.entries,
    pages: Map<ChannelPage, ChannelDetailPageComposable> = emptyMap(),
    pageScope: ChannelDetailPageScope,
) {
    Column(Modifier.fillMaxSize()) {
        ChannelDetailHeader(channelDetail)
        ChannelDetailPager(tabs) { page ->
            AppLogger.logD("ChannelDetail") { "page:$page" }
            pages[page]?.invoke(pageScope)
        }
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
    pageContent: @Composable PagerScope.(ChannelPage) -> Unit,
) {
    HorizontalPagerWithTabScreen(
        edgePadding = 0.dp,
        tabCount = pages.size,
        tab = { pages[it].name },
    ) {
        pageContent(pages[it])
    }
}

interface ChannelDetailPageScope {
    val delegate: ChannelDetailDelegate
    val dialogState: LinkAnnotationDialogState

    companion object {
        internal fun create(
            delegate: ChannelDetailDelegate,
            dialogState: LinkAnnotationDialogState,
        ): ChannelDetailPageScope = object : ChannelDetailPageScope {
            override val delegate: ChannelDetailDelegate get() = delegate
            override val dialogState: LinkAnnotationDialogState get() = dialogState
        }
    }
}

typealias ChannelDetailPageComposable = @Composable ChannelDetailPageScope.() -> Unit

private val pages = mapOf<ChannelPage, ChannelDetailPageComposable>(
    ChannelPage.ABOUT to {
        val desc = delegate.annotatedDetail.collectAsState(AnnotatableString.empty())
        AnnotatedTextPage(textProvider = { desc.value }, dialog = dialogState)
    },
    ChannelPage.CHANNEL_SECTION to {
        val sectionState = delegate.channelSection.collectAsState(emptyList())
        PlainListPage(
            listProvider = { sectionState.value },
            idProvider = { it.id },
            content = { cs -> ChannelSectionContent(cs) },
        )
    },
    ChannelPage.UPLOADED to {
        val itemsState = delegate.uploadedVideo.collectAsState(emptyList())
        PlainListPage(
            listProvider = { itemsState.value },
            idProvider = { it.id },
            content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
        )
    },
    ChannelPage.ACTIVITIES to {
        val logs = delegate.activities.collectAsState(initial = emptyList())
        PlainListPage(
            listProvider = { logs.value },
            idProvider = { it.id },
            content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
        )
    },
    ChannelPage.DEBUG_CHANNEL to {
        val text = delegate.channelDetailBody.collectAsState(initial = null)
        PlainTextPage { text.value?.toString() ?: "" }
    },
)

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
private fun AnnotatedTextPage(
    textProvider: () -> AnnotatableString,
    dialog: LinkAnnotationDialogState,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        AnnotatableText(
            fontSize = 14.sp,
            annotatableString = textProvider(),
            dialog = dialog,
        )
    }
}

@Composable
private fun <T> PlainListPage(
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
private fun VideoListItem(
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
            ImageLoadableView.Thumbnail(
                url = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )
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
private fun SinglePlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
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
private fun MultiPlaylistContent(item: LiveVideoThumbnail, modifier: Modifier = Modifier) {
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
private fun MultiChannelContent(item: LiveChannel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            LiveChannelIcon(
                iconUrl = item.iconUrl,
                iconSize = 48.dp,
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
private fun ChannelScreenPreview() {
    val channelDetail = object : LiveChannelDetailBody {
        override val id: LiveChannel.Id get() = YouTubeVideo.Id("a").mapTo()
        override val title: String = "channel title"
        override val statsText: String get() = "@custom_url・Subscribers:52.4k・Videos:132・Views:38,498,283・Published:2021/04/13"
        override val bannerUrl: String? get() = null
        override val iconUrl: String get() = ""
        override val platform: LivePlatform get() = YouTube
        override fun equals(other: Any?): Boolean = throw NotImplementedError()
        override fun hashCode(): Int = throw NotImplementedError()
    }
    val pageScope = object : ChannelDetailPageScope {
        override val delegate: ChannelDetailDelegate
            get() = object : ChannelDetailDelegate {
                override val annotatedDetail: Flow<AnnotatableString> = flowOf(
                    AnnotatableString.create(
                        annotatable = "text.",
                        accountUrlCreator = { emptyList() },
                    )
                )
                override val uploadedVideo: Flow<List<LiveVideoThumbnail>>
                    get() = TODO("Not yet implemented")
                override val channelSection: Flow<List<ChannelDetailChannelSection>>
                    get() = TODO("Not yet implemented")
                override val activities: Flow<List<LiveVideo<*>>>
                    get() = TODO("Not yet implemented")
                override val tabs: List<ChannelPage>
                    get() = TODO("Not yet implemented")
                override val channelDetailBody: Flow<LiveChannelDetailBody?>
                    get() = TODO("Not yet implemented")
            }
        override val dialogState: LinkAnnotationDialogState = LinkAnnotationDialogState()
    }
    AppTheme {
        ChannelDetailScreen(
            channelDetail = { channelDetail },
            pageScope = pageScope,
        )
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

@LightDarkModePreview
@Composable
private fun ChannelListItemPreview() {
    AppTheme {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(5) {
                MultiChannelContent(
                    item = LiveChannelEntity(
                        id = LiveChannel.Id("channel_$it", YouTubeChannel.Id::class),
                        title = "example_$it channel",
                        platform = YouTube,
                        iconUrl = "",
                    ),
                    modifier = Modifier.fillParentMaxWidth(0.3f),
                )
            }
        }
    }
}
