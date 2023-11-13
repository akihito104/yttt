package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.ChannelDetailChannelSection
import com.freshdigitable.yttt.ChannelPage
import com.freshdigitable.yttt.ChannelViewModel
import com.freshdigitable.yttt.CustomCrop
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelDetailEntity
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEntity
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.dateFormatter
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.util.*

@Composable
fun ChannelDetailScreen(
    id: LiveChannel.Id,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val detail = viewModel.fetchChannel(id)
    val items = viewModel.fetchVideoListItems(detail)
    val detailState = detail.observeAsState()
    ChannelDetailScreen(
        pages = ChannelPage.findByPlatform(id.platform),
        channelDetail = { detailState.value }) { page ->
        when (page) {
            ChannelPage.ABOUT -> PlainTextPage {
                detailState.value?.description ?: ""
            }

            ChannelPage.DEBUG_CHANNEL -> PlainTextPage {
                detailState.value?.toString() ?: ""
            }

            ChannelPage.CHANNEL_SECTION -> {
                val sectionState = viewModel.fetchChannelSection(id).observeAsState(emptyList())
                PlainListPage(
                    listProvider = { sectionState.value },
                    idProvider = { it.id },
                    content = { cs -> ChannelSectionContent(cs) },
                )
            }

            ChannelPage.UPLOADED -> {
                val itemsState = items.observeAsState(emptyList())
                PlainListPage(
                    listProvider = { itemsState.value },
                    idProvider = { it.id },
                    content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
                )
            }

            ChannelPage.ACTIVITIES -> {
                val logs = viewModel.fetchActivities(id).observeAsState(emptyList())
                PlainListPage(
                    listProvider = { logs.value },
                    idProvider = { it.id },
                    content = { VideoListItem(thumbnailUrl = it.thumbnailUrl, title = it.title) },
                )
            }
        }
    }
}

@Composable
private fun ChannelDetailScreen(
    channelDetail: () -> LiveChannelDetail?,
    pages: Array<ChannelPage> = ChannelPage.values(),
    pageContent: @Composable (ChannelPage) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ChannelDetailHeader(channelDetail)
        ChannelDetailPager(pages, pageContent)
    }
}

@Composable
@OptIn(ExperimentalGlideComposeApi::class)
private fun ChannelDetailHeader(
    channelDetailProvider: () -> LiveChannelDetail?,
) {
    val channelDetail = channelDetailProvider() ?: return
    val subscriberCount = if (!channelDetail.isSubscriberHidden)
        "Subscribers:${channelDetail.subscriberCount.toStringWithUnitPrefix}"
    else null
    val statsText = listOfNotNull(
        channelDetail.customUrl,
        subscriberCount,
        "Videos:${channelDetail.videoCount}",
        "Views:${channelDetail.viewsCount.toStringWithComma}",
        "Published:${channelDetail.publishedAt.toLocalFormattedText(dateFormatter)}",
    ).joinToString("・")
    Column {
        if (channelDetail.bannerUrl?.isNotEmpty() == true) {
            GlideImage(
                model = channelDetail.bannerUrl,
                contentDescription = "",
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(32f / 9f),
                requestBuilderTransform = {
                    it.transform(CustomCrop(width = 1253, height = 338))
                },
            )
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
                text = statsText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChannelDetailPager(
    pages: Array<ChannelPage>,
    pageContent: @Composable (ChannelPage) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState { pages.size }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
        ) {
            val coroutineScope = rememberCoroutineScope()
            pages.forEachIndexed { i, p ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(i)
                        }
                    },
                    text = { Text(text = p.name) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            key = { i -> pages[i].ordinal },
        ) { index ->
            pageContent(pages[index])
        }
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

class VideoListItemEntity(val id: IdBase, val thumbnailUrl: String, val title: String)

@OptIn(ExperimentalGlideComposeApi::class)
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
                GlideImage(
                    model = thumbnailUrl,
                    contentDescription = "",
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
private fun ChannelSectionContent(cs: YouTubeChannelSection) {
    Column {
        Text(text = cs.title ?: cs.type.name)
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
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SinglePlaylistContent(item: YouTubePlaylistItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        GlideImage(
            model = item.thumbnailUrl,
            contentDescription = "",
            modifier = Modifier.aspectRatio(16 / 9f),
        )
        Text(
            text = item.title,
            maxLines = 2,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MultiPlaylistContent(item: YouTubePlaylist, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        GlideImage(
            model = item.thumbnailUrl,
            contentDescription = "",
            modifier = Modifier.aspectRatio(16 / 9f),
        )
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

private val BigInteger.toStringWithComma: String
    get() = NumberFormat.getNumberInstance(Locale.US).format(this)
private val BigInteger.toStringWithUnitPrefix: String
    get() {
        val decimal = this.toBigDecimal()
        val precision = decimal.precision()
        return if (precision <= 3) {
            this.toString()
        } else {
            val prefixGrade = (precision - 1) / 3
            val shift = prefixGrade * 3
            val digit = DecimalFormat("#.##").format(decimal.movePointLeft(shift))
            "${digit}${unitPrefix[prefixGrade]}"
        }
    }
private val unitPrefix = arrayOf("", "k", "M", "G", "T", "P", "E")

@LightDarkModePreview
@Composable
fun ChannelScreenPreview() {
    AppTheme {
        ChannelDetailScreen({
            LiveChannelDetailEntity(
                id = YouTubeVideo.Id("a").mapTo(),
                title = "channel title",
                iconUrl = "",
                bannerUrl = "",
                subscriberCount = BigInteger.valueOf(52400),
                isSubscriberHidden = false,
                videoCount = BigInteger.valueOf(132),
                viewsCount = BigInteger.valueOf(38498283),
                publishedAt = Instant.parse("2021-04-13T00:23:11Z"),
                customUrl = "@custom_url",
                keywords = emptyList(),
                description = "description is here.",
                uploadedPlayList = YouTubePlaylist.Id("a"),
            )
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
