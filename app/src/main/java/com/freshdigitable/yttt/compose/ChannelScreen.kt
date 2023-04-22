package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.freshdigitable.yttt.ChannelPage
import com.freshdigitable.yttt.ChannelViewModel
import com.freshdigitable.yttt.CustomCrop
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.google.accompanist.themeadapter.material.MdcTheme
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun ChannelScreen(
    id: LiveChannel.Id,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val detail = viewModel.fetchChannel(id).observeAsState().value
    val section = viewModel.fetchChannelSection(id).observeAsState(emptyList()).value
    if (detail != null) {
        ChannelScreen(channelDetail = detail, channelSection = section)
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelScreen(
    channelDetail: LiveChannelDetail,
    channelSection: List<LiveChannelSection>,
) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
    val statsText = listOfNotNull(
        channelDetail.customUrl,
        if (!channelDetail.isSubscriberHidden) "Subscribers:${channelDetail.subscriberCount.toStringWithUnitPrefix}" else null,
        "Videos:${channelDetail.videoCount}",
        "Views:${channelDetail.viewsCount.toStringWithComma}",
        "Published:${channelDetail.publishedAt.toLocalFormattedText}",
    ).joinToString("・")
    Column(Modifier.fillMaxSize()) {
        if (channelDetail.bannerUrl != null) {
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
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
        ) {
            ChannelPage.values().forEach { p ->
                Tab(
                    selected = pagerState.currentPage == p.ordinal,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(p.ordinal)
                        }
                    },
                    text = { Text(text = p.name) }
                )
            }
        }
        HorizontalPager(
            pageCount = ChannelPage.values().size,
            state = pagerState,
            modifier = Modifier.align(Start)
        ) {
            ChannelTabPage(it, channelDetail, channelSection)
        }
    }
}

@Composable
private fun ChannelTabPage(
    it: Int,
    channelDetail: LiveChannelDetail,
    channelSection: List<LiveChannelSection>,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            text = ChannelPage.values()[it].pageText(channelDetail, channelSection),
            textAlign = TextAlign.Start,
        )
    }
}

private fun ChannelPage.pageText(
    detail: LiveChannelDetail,
    channelSection: List<LiveChannelSection>
): String {
    return when (this) {
        ChannelPage.ABOUT -> detail.description ?: ""
        ChannelPage.DEBUG_CHANNEL -> detail.toString()
        ChannelPage.DEBUG_CHANNEL_SECTION -> channelSection.toString()
    }
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val Instant.toLocalFormattedText: String
    get() {
        val localDateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
        return localDateTime.format(dateTimeFormatter)
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

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun ChannelScreenPreview() {
    MdcTheme {
        ChannelScreen(object : LiveChannelDetail, LiveChannel by LiveChannelEntity(
            id = LiveChannel.Id("a"),
            title = "channel title",
            iconUrl = "",
        ) {
            override val bannerUrl: String = ""
            override val subscriberCount: BigInteger = BigInteger.valueOf(52400)
            override val isSubscriberHidden: Boolean = false
            override val videoCount: BigInteger = BigInteger.valueOf(132)
            override val viewsCount: BigInteger = BigInteger.valueOf(38498283)
            override val publishedAt: Instant = Instant.parse("2021-04-13T00:23:11Z")
            override val customUrl: String = "@custom_url"
            override val keywords: Collection<String> = emptyList()
            override val description: String = "description is here."
        }, emptyList())
    }
}
