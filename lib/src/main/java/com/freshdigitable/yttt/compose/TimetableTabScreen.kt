package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.TimetableTabViewModel
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableTabScreen(
    viewModel: TimetableTabViewModel = hiltViewModel(),
    onListItemClicked: (LiveVideo.Id) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val tabData = viewModel.tabs.collectAsState(initial = TabData.initialValues())
    val refreshing = viewModel.isLoading.observeAsState(false)
    val onMenuClicked: (LiveVideo.Id) -> Unit = viewModel::onMenuClicked
    val listContents: List<LazyListScope.() -> Unit> = TimetablePage.values().map {
        when (it.type) {
            TimetablePage.Type.SIMPLE -> {
                val item = viewModel.getSimpleItemList(it).collectAsState(initial = emptyList())
                return@map { simpleContent({ item.value }, onListItemClicked, onMenuClicked) }
            }

            TimetablePage.Type.GROUPED -> {
                val item = viewModel.getGroupedItemList(it).collectAsState(initial = emptyMap())
                return@map { groupedContent({ item.value }, onListItemClicked, onMenuClicked) }
            }
        }
    }
    TimetableTabScreen(
        tabDataProvider = { tabData.value },
    ) { index ->
        TimetableScreen(
            refreshingProvider = { refreshing.value },
            onRefresh = viewModel::loadList,
            listContent = listContents[index],
        )
    }
    val menuItems = viewModel.menuItems.collectAsState(emptyList())
    val sheetState = rememberModalBottomSheetState()
    ListItemMenuSheet(
        menuItemsProvider = { menuItems.value },
        sheetState = sheetState,
        onMenuItemClicked = { viewModel.onMenuItemClicked(it) },
        onDismissRequest = viewModel::onMenuClosed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItemMenuSheet(
    menuItemsProvider: () -> Collection<TimetableMenuItem>,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onMenuItemClicked: (TimetableMenuItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val menuItems = menuItemsProvider()
    if (menuItems.isNotEmpty()) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
        ) {
            MenuContent(menuItems = menuItems) {
                onMenuItemClicked(it)
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
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
    menuItems: Collection<TimetableMenuItem> = TimetableMenuItem.values().toList(),
    onMenuClicked: (TimetableMenuItem) -> Unit,
) {
    menuItems.forEach { i ->
        ListItem(
            modifier = Modifier.clickable(onClick = { onMenuClicked(i) }),
            headlineContent = { Text(i.text) },
        )
    }
    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
}

enum class TimetableMenuItem(val text: String) {
    ADD_FREE_CHAT("check as free chat"),
    REMOVE_FREE_CHAT("uncheck as free chat"),
    LAUNCH_LIVE("watch live"),
    ;
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableTabScreen(
    tabDataProvider: () -> List<TabData>,
    page: @Composable (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val tabData = tabDataProvider()
        val pagerState = rememberPagerState { tabData.size }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.wrapContentSize(),
        ) {
            val coroutineScope = rememberCoroutineScope()
            tabData.forEachIndexed { index, data ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = data.text()) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            pageContent = { page(it) },
        )
    }
}

@Immutable
class TabData(
    private val page: TimetablePage,
    private val count: Int
) : Comparable<TabData> {
    @Composable
    @ReadOnlyComposable
    fun text(): String = stringResource(id = page.textRes, count)
    override fun compareTo(other: TabData): Int = page.ordinal - other.page.ordinal

    companion object {
        fun initialValues(): List<TabData> {
            return TimetablePage.values().map { TabData(it, 0) }
        }
    }
}

@LightModePreview
@Composable
private fun TimetableTabScreenPreview() {
    AppTheme {
        TimetableTabScreen(
            tabDataProvider = {
                listOf(
                    TabData(TimetablePage.OnAir, 10),
                    TabData(TimetablePage.Upcoming, 3),
                    TabData(TimetablePage.FreeChat, 7),
                )
            },
        ) { Text("page: $it") }
    }
}

@LightModePreview
@Composable
private fun ModalSheetPreview() {
    AppTheme {
        Column(Modifier.fillMaxWidth()) {
            MenuContent {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@LightModePreview
@Composable
private fun ListItemMenuSheetPreview() {
    AppTheme {
        ListItemMenuSheet(
            menuItemsProvider = { TimetableMenuItem.values().toList() },
            onMenuItemClicked = {},
        ) {}
    }
}