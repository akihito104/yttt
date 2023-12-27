package com.freshdigitable.yttt.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.feature.timetable.TimetableTabViewModel
import com.freshdigitable.yttt.feature.timetable.textRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableTabScreen(
    viewModel: TimetableTabViewModel = hiltViewModel(),
    onListItemClicked: (LiveVideo.Id) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val tabData = viewModel.tabs.collectAsState(initial = TimetableTabData.initialValues())
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
    HorizontalPagerWithTabScreen(
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

@Immutable
internal class TimetableTabData(
    private val page: TimetablePage,
    private val count: Int
) : TabData {
    @Composable
    @ReadOnlyComposable
    override fun title(): String = stringResource(id = page.textRes, count)
    override fun compareTo(other: TabData): Int {
        val o = other as? TimetableTabData ?: return -1
        return page.ordinal - o.page.ordinal
    }

    companion object {
        fun initialValues(): List<TimetableTabData> {
            return TimetablePage.values().map { TimetableTabData(it, 0) }
        }
    }
}

@LightModePreview
@Composable
private fun TimetableTabScreenPreview() {
    AppTheme {
        HorizontalPagerWithTabScreen(
            tabDataProvider = {
                listOf(
                    TimetableTabData(TimetablePage.OnAir, 10),
                    TimetableTabData(TimetablePage.Upcoming, 3),
                    TimetableTabData(TimetablePage.FreeChat, 7),
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
