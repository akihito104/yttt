package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.MainViewModel
import com.freshdigitable.yttt.OnAirListViewModel
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.UpcomingListViewModel
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableTabScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onAirViewModel: OnAirListViewModel = hiltViewModel(),
    upcomingViewModel: UpcomingListViewModel = hiltViewModel(),
    onListItemClicked: (LiveVideo.Id) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val tabData = combine(
        onAirViewModel.tabData,
        upcomingViewModel.tabData,
        upcomingViewModel.freeChatTab,
    ) { items ->
        items.toList()
    }.collectAsState(initial = TimetablePage.values().map { TabData(it, 0) })
    val refreshing = viewModel.isLoading.observeAsState(false)
    val onMenuClicked: (LiveVideo.Id) -> Unit = viewModel::onMenuClicked
    val listContents: List<LazyListScope.() -> Unit> = TimetablePage.values().map {
        when (it) {
            TimetablePage.OnAir -> {
                val onAir = onAirViewModel.items.collectAsState(emptyList())
                return@map { simpleContent({ onAir.value }, onListItemClicked, onMenuClicked) }
            }

            TimetablePage.Upcoming -> {
                val upcoming = upcomingViewModel.items.collectAsState(emptyMap())
                return@map { groupedContent({ upcoming.value }, onListItemClicked, onMenuClicked) }
            }

            TimetablePage.FreeChat -> {
                val freeChat = upcomingViewModel.freeChat.collectAsState(emptyList())
                return@map { simpleContent({ freeChat.value }, onListItemClicked, onMenuClicked) }
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
        onMenuItemClicked = viewModel::onMenuItemClicked,
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
    Spacer(modifier = Modifier.navigationBarsPadding())
}

enum class TimetableMenuItem(val text: String) {
    ADD_FREE_CHAT("check as free chat"),
    REMOVE_FREE_CHAT("uncheck as free chat"),
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
) {
    @Composable
    @ReadOnlyComposable
    fun text(): String = stringResource(id = page.textRes, count)
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
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

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ModalSheetPreview() {
    AppTheme {
        Column(Modifier.fillMaxWidth()) {
            MenuContent {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ListItemMenuSheetPreview() {
    AppTheme {
        ListItemMenuSheet(
            menuItemsProvider = { TimetableMenuItem.values().toList() },
            onMenuItemClicked = {},
        ) {}
    }
}
