package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshdigitable.yttt.compose.LiveVideoPreviewParamProvider.Companion.liveVideo
import com.freshdigitable.yttt.compose.LiveVideoPreviewParamProvider.Companion.timelineItem
import com.freshdigitable.yttt.compose.preview.LightDarkModePreview
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.timetable.TimelineItem

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TimetableScreen(
    lazyListState: LazyListState = rememberLazyListState(),
    refreshingProvider: () -> Boolean,
    onRefresh: () -> Unit,
    listContent: LazyListScope.() -> Unit,
) {
    val refreshing = refreshingProvider()
    val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = listContent,
        )
        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

fun LazyListScope.simpleContent(
    itemsProvider: () -> List<TimelineItem>,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    onListItemClicked: (LiveVideo.Id) -> Unit,
    onMenuClicked: (LiveVideo.Id) -> Unit,
) {
    itemsIndexed(
        items = itemsProvider(),
        key = { _, item -> item.id.value }) { _, item ->
        LiveVideoListItemView(
            video = item,
            thumbnailModifier = thumbnailModifier(item.id),
            titleModifier = titleModifier(item.id),
            onItemClick = { onListItemClicked(item.id) },
            onMenuClicked = { onMenuClicked(item.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.groupedContent(
    itemsProvider: () -> Map<String, List<TimelineItem>>,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    onListItemClicked: (LiveVideo.Id) -> Unit,
    onMenuClicked: (LiveVideo.Id) -> Unit,
) {
    itemsProvider().forEach { (datetime, items) ->
        stickyHeader(key = datetime) {
            LiveVideoHeaderView(label = datetime)
        }
        itemsIndexed(
            items = items,
            key = { _, item -> item.id.value }) { _, item ->
            LiveVideoListItemView(
                video = item,
                thumbnailModifier = thumbnailModifier(item.id),
                titleModifier = titleModifier(item.id),
                onItemClick = { onListItemClicked(item.id) },
                onMenuClicked = { onMenuClicked(item.id) },
            )
        }
    }
}

@LightModePreview
@Composable
private fun SimpleTimetableScreenPreview() {
    AppTheme {
        TimetableScreen(
            refreshingProvider = { false },
            onRefresh = {},
        ) {
            simpleContent(
                itemsProvider = { listOf(timelineItem(liveVideo())) },
                onListItemClicked = {},
            ) {}
        }
    }
}

@LightDarkModePreview
@Composable
private fun GroupedTimetableScreenPreview() {
    val items = mapOf("2023/06/29(木)" to listOf(timelineItem(liveVideo())))
    AppTheme {
        TimetableScreen(
            refreshingProvider = { false },
            onRefresh = {},
        ) {
            groupedContent(
                itemsProvider = { items },
                onListItemClicked = {},
            ) {}
        }
    }
}
