package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.LiveVideoPreviewParamProvider.Companion.freeChat
import com.freshdigitable.yttt.compose.LiveVideoPreviewParamProvider.Companion.upcomingVideo
import com.freshdigitable.yttt.compose.preview.PreviewLightDarkMode
import com.freshdigitable.yttt.compose.preview.PreviewLightMode
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.timetable.GroupKey
import com.freshdigitable.yttt.feature.timetable.TimeAdjustment
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.logD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    page: TimetablePage,
    itemProvider: () -> List<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    refreshingProvider: () -> Boolean,
    onRefresh: () -> Unit,
    onListItemClick: (LiveVideo.Id) -> Unit,
    onMenuClick: (LiveVideo.Id) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    lazyListState: LazyListState = rememberLazyListState(),
) {
    AppLogger.logD("Timetable") { "start:" }
    PullToRefreshBox(
        isRefreshing = refreshingProvider(),
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = timetableContent(
                page,
                itemProvider,
                timeAdjustmentProvider,
            ) { item, timeAdjustment ->
                LiveVideoListItemView(
                    video = item,
                    timeAdjustment = timeAdjustment,
                    modifier = Modifier.animateItem(),
                    thumbnailModifier = thumbnailModifier(item.id),
                    titleModifier = titleModifier(item.id),
                    onItemClick = { onListItemClick(item.id) },
                    onMenuClick = { onMenuClick(item.id) },
                )
            },
        )
    }
}

private fun timetableContent(
    page: TimetablePage,
    itemProvider: () -> List<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    content: @Composable LazyItemScope.(LiveTimelineItem, TimeAdjustment) -> Unit,
): LazyListScope.() -> Unit = when (page.type) {
    TimetablePage.Type.SIMPLE -> {
        {
            simpleContent(
                itemsProvider = itemProvider,
                timeAdjustmentProvider = timeAdjustmentProvider,
                content = content,
            )
        }
    }

    TimetablePage.Type.GROUPED -> {
        {
            groupedContent(
                itemsProvider = itemProvider,
                timeAdjustmentProvider = timeAdjustmentProvider,
                content = content,
            )
        }
    }
}

private fun LazyListScope.simpleContent(
    itemsProvider: () -> List<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    content: @Composable LazyItemScope.(LiveTimelineItem, TimeAdjustment) -> Unit,
) {
    itemsIndexed(
        items = itemsProvider(),
        key = { _, item -> item.id.value },
    ) { _, item ->
        content(item, timeAdjustmentProvider())
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.groupedContent(
    itemsProvider: () -> List<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    content: @Composable LazyItemScope.(LiveTimelineItem, TimeAdjustment) -> Unit,
) {
    val timeAdjustment = timeAdjustmentProvider()
    val grouped = itemsProvider().groupBy {
        GroupKey.create(it.dateTime, timeAdjustment.extraHourOfDay, timeAdjustment.zoneId)
    }.mapKeys { it.key.text }
    grouped.forEach { (datetime, items) ->
        stickyHeader(key = datetime) {
            LiveVideoHeaderView(
                label = datetime,
                modifier = Modifier.animateItem(),
            )
        }
        simpleContent(
            itemsProvider = { items },
            timeAdjustmentProvider = timeAdjustmentProvider,
        ) { item, timeAdjustment ->
            content(item, timeAdjustment)
        }
    }
}

@PreviewLightMode
@Composable
private fun SimpleTimetableScreenPreview() {
    AppTheme {
        TimetableScreen(
            page = TimetablePage.FreeChat,
            itemProvider = { listOf(freeChat()) },
            refreshingProvider = { false },
            onRefresh = {},
            timeAdjustmentProvider = { TimeAdjustment.zero() },
            onListItemClick = {},
            onMenuClick = {},
        )
    }
}

@PreviewLightDarkMode
@Composable
private fun GroupedTimetableScreenPreview() {
    val items = listOf(upcomingVideo())
    AppTheme {
        TimetableScreen(
            refreshingProvider = { false },
            itemProvider = { items },
            timeAdjustmentProvider = { TimeAdjustment.zero() },
            onRefresh = {},
            page = TimetablePage.Upcoming,
            onListItemClick = {},
            onMenuClick = {},
        )
    }
}
