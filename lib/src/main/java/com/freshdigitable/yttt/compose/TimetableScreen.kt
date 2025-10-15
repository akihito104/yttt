package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    page: TimetablePage,
    itemProvider: () -> LazyPagingItems<LiveTimelineItem>,
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
    itemProvider: () -> LazyPagingItems<LiveTimelineItem>,
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
    itemsProvider: () -> LazyPagingItems<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    content: @Composable LazyItemScope.(LiveTimelineItem, TimeAdjustment) -> Unit,
) {
    val items = itemsProvider()
    items(
        count = items.itemCount,
        key = items.itemKey { "${it.id.type.simpleName}_${it.id.value}" },
    ) { i ->
        val item = items[i] ?: return@items
        content(item, timeAdjustmentProvider())
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.groupedContent(
    itemsProvider: () -> LazyPagingItems<LiveTimelineItem>,
    timeAdjustmentProvider: () -> TimeAdjustment,
    content: @Composable LazyItemScope.(LiveTimelineItem, TimeAdjustment) -> Unit,
) {
    val pagedItems = itemsProvider()
    val timeAdjustment = timeAdjustmentProvider()
    val grouped = buildMap {
        for (i in 0 until pagedItems.itemSnapshotList.items.size) {
            val index = i + pagedItems.itemSnapshotList.placeholdersBefore
            val item = pagedItems.peek(index) ?: continue
            val key = GroupKey.create(item.dateTime, timeAdjustment.extraHourOfDay, timeAdjustment.zoneId)
            val list = getOrPut(key) { mutableListOf() }
            list.add(index)
        }
    }.mapKeys { it.key.text }
    grouped.forEach { (datetime, indices) ->
        stickyHeader(key = datetime) {
            LiveVideoHeaderView(
                label = datetime,
                modifier = Modifier.animateItem(),
            )
        }
        items(
            items = indices,
            key = { i -> pagedItems.itemKey { "${it.id.type.simpleName}_${it.id.value}" }(i) },
        ) { index ->
            val item = pagedItems[index] ?: return@items
            content(item, timeAdjustment)
        }
    }
}

@PreviewLightMode
@Composable
private fun SimpleTimetableScreenPreview() {
    val page = MutableStateFlow(
        PagingData.from(
            data = listOf(freeChat()),
        ),
    ).collectAsLazyPagingItems()
    AppTheme {
        TimetableScreen(
            page = TimetablePage.FreeChat,
            itemProvider = { page },
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
    val items = MutableStateFlow(
        PagingData.from(
            data = listOf(upcomingVideo()),
        ),
    ).collectAsLazyPagingItems()
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
