package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.preview.PreviewLightMode
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.timetable.ContextMenuSelector
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.feature.timetable.TimetableTabViewModel
import com.freshdigitable.yttt.feature.timetable.textRes
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableTabScreen(
    viewModel: TimetableTabViewModel,
    onListItemClick: (LiveVideo.Id) -> Unit,
    topAppBarState: TopAppBarStateHolder,
    modifier: Modifier = Modifier,
    tabModifier: Modifier = Modifier,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
) {
    AppLogger.logD("TimetableTab") { "start:" }
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val refreshing = viewModel.isLoading.observeAsState(false)
    topAppBarState.setup(
        enabled = { !refreshing.value },
        consume = viewModel::loadList,
    )
    val listState = TimetablePage.entries.associateWith { rememberLazyListState() }
    val timeAdjustment = viewModel.timeAdjustment.collectAsState()
    val items = TimetablePage.entries.associate {
        it.ordinal to (it to checkNotNull(viewModel.pagers[it]).collectAsLazyPagingItems())
    }
    HorizontalPagerWithTabScreen(
        tabCount = TimetablePage.entries.size,
        tab = { index ->
            val (page, item) = checkNotNull(items[index])
            TimetableTabData(page, item.itemCount).title()
        },
        modifier = modifier,
        tabModifier = tabModifier,
    ) { index ->
        val (page, item) = checkNotNull(items[index])
        TimetableScreen(
            page = page,
            itemProvider = { item },
            timeAdjustmentProvider = { timeAdjustment.value },
            lazyListState = checkNotNull(listState[page]),
            refreshingProvider = { refreshing.value },
            titleModifier = titleModifier,
            thumbnailModifier = thumbnailModifier,
            onRefresh = viewModel::loadList,
            onListItemClick = onListItemClick,
            onMenuClick = viewModel::onMenuClick,
        )
    }
    val menuSelector = viewModel.menuSelector.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    ListItemMenuSheet(
        menuSelectorProvider = { menuSelector.value },
        sheetState = sheetState,
        onDismissRequest = viewModel::onMenuClose,
    )
}

private fun TopAppBarStateHolder.setup(
    enabled: () -> Boolean,
    consume: suspend () -> Unit,
) {
    updateMenuItems(
        listOf(
            TopAppBarMenuItem.onAppBar(
                text = "reload",
                icon = Icons.Default.Refresh,
                enabled = enabled,
                consume = consume,
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListItemMenuSheet(
    menuSelectorProvider: () -> ContextMenuSelector<TimetableMenuItem>,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val menuSelector = menuSelectorProvider()
    if (menuSelector.menuItems.isNotEmpty()) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
        ) {
            MenuContent(menuItems = menuSelector.menuItems) {
                coroutineScope.launch {
                    menuSelector.consumeMenuItem(it)
                    sheetState.hide()
                }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MenuContent(
    menuItems: Collection<TimetableMenuItem> = TimetableMenuItem.entries,
    onMenuClick: (TimetableMenuItem) -> Unit,
) {
    menuItems.forEach { i ->
        ListItem(
            modifier = Modifier.clickable(onClick = { onMenuClick(i) }),
            headlineContent = { Text(i.text) },
        )
    }
    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
}

@Immutable
internal class TimetableTabData(
    internal val page: TimetablePage,
    private val count: Int,
) : TabData<TimetableTabData> {
    @Composable
    @ReadOnlyComposable
    override fun title(): String = stringResource(id = page.textRes, count)
    override fun compareTo(other: TimetableTabData): Int = page.ordinal - other.page.ordinal
}

@PreviewLightMode
@Composable
private fun TimetableTabScreenPreview() {
    val tabs = listOf(
        TimetableTabData(TimetablePage.OnAir, count = 10),
        TimetableTabData(TimetablePage.Upcoming, count = 3),
        TimetableTabData(TimetablePage.FreeChat, count = 7),
    )
    AppTheme {
        HorizontalPagerWithTabScreen(
            tabProvider = { tabs },
        ) { Text("page: ${it.page.name}") }
    }
}

@PreviewLightMode
@Composable
private fun ModalSheetPreview() {
    AppTheme {
        Column(Modifier.fillMaxWidth()) {
            MenuContent {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightMode
@Composable
private fun ListItemMenuSheetPreview() {
    AppTheme {
        ListItemMenuSheet(
            menuSelectorProvider = {
                object : ContextMenuSelector<TimetableMenuItem> {
                    override val menuItems: List<TimetableMenuItem> = TimetableMenuItem.entries
                    override suspend fun consumeMenuItem(item: TimetableMenuItem) = Unit
                }
            },
        ) {}
    }
}
