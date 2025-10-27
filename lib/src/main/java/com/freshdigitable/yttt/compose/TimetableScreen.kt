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
    pagingItem: LazyPagingItems<LiveVideo>,
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
    AppLogger.logD("Timetable") { "start: $page" }
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
                pagingItem,
                timeAdjustmentProvider(),
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
    pagingItem: LazyPagingItems<LiveVideo>,
    timeAdjustment: TimeAdjustment,
    content: @Composable LazyItemScope.(LiveVideo, TimeAdjustment) -> Unit,
): LazyListScope.() -> Unit = when (page.type) {
    TimetablePage.Type.SIMPLE -> {
        {
            simpleContent(
                pagingItems = pagingItem,
                timeAdjustment = timeAdjustment,
                content = content,
            )
        }
    }

    TimetablePage.Type.GROUPED -> {
        {
            groupedContent(
                pagingItems = pagingItem,
                timeAdjustment = timeAdjustment,
                content = content,
            )
        }
    }
}

private fun LazyListScope.simpleContent(
    pagingItems: LazyPagingItems<LiveVideo>,
    timeAdjustment: TimeAdjustment,
    content: @Composable LazyItemScope.(LiveVideo, TimeAdjustment) -> Unit,
) {
    AppLogger.logD("simpleContent:") { "start:" }
    items(
        count = pagingItems.itemCount,
        key = pagingItems.itemKey { it.itemKey },
    ) { i ->
        val item = pagingItems[i] ?: return@items
        content(item, timeAdjustment)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.groupedContent(
    pagingItems: LazyPagingItems<LiveVideo>,
    timeAdjustment: TimeAdjustment,
    content: @Composable LazyItemScope.(LiveVideo, TimeAdjustment) -> Unit,
) {
    AppLogger.logD("groupedContent:") { "start:" }
    val grouped = buildMap {
        for (i in 0 until pagingItems.itemSnapshotList.items.size) {
            val index = i + pagingItems.itemSnapshotList.placeholdersBefore
            val item = pagingItems.peek(index) ?: continue
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
            key = { i -> pagingItems.itemKey { it.itemKey }(i) },
        ) { index ->
            val item = pagingItems[index] ?: return@items
            content(item, timeAdjustment)
        }
    }
}

private val LiveVideo.itemKey: String get() = "${id.type.qualifiedName}_${id.value}"

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
            pagingItem = page,
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
            pagingItem = items,
            timeAdjustmentProvider = { TimeAdjustment.zero() },
            onRefresh = {},
            page = TimetablePage.Upcoming,
            onListItemClick = {},
            onMenuClick = {},
        )
    }
}
