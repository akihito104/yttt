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
    tabModifier: Modifier = Modifier,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
) {
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val tabData = viewModel.tabs.collectAsState(initial = TimetableTabData.initialValues())
    val refreshing = viewModel.isLoading.observeAsState(false)
    val listContents: Map<TimetablePage, LazyListScope.() -> Unit> = TimetablePage.entries
        .associateWith {
            timetableContent(
                it,
                thumbnailModifier = thumbnailModifier,
                titleModifier = titleModifier,
                onListItemClicked,
                viewModel,
            )
        }
    HorizontalPagerWithTabScreen(
        tabModifier = tabModifier,
        tabDataProvider = { tabData.value },
    ) { tab ->
        val p = (tab as TimetableTabData).page
        TimetableScreen(
            refreshingProvider = { refreshing.value },
            onRefresh = viewModel::loadList,
            listContent = checkNotNull(listContents[p]),
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

@Composable
private fun timetableContent(
    page: TimetablePage,
    thumbnailModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    titleModifier: @Composable (LiveVideo.Id) -> Modifier = { Modifier },
    onListItemClicked: (LiveVideo.Id) -> Unit,
    viewModel: TimetableTabViewModel,
): LazyListScope.() -> Unit {
    when (page.type) {
        TimetablePage.Type.SIMPLE -> {
            val item = viewModel.getSimpleItemList(page).collectAsState(initial = emptyList())
            return {
                simpleContent(
                    { item.value },
                    thumbnailModifier = thumbnailModifier,
                    titleModifier = titleModifier,
                    onListItemClicked,
                    viewModel::onMenuClicked,
                )
            }
        }

        TimetablePage.Type.GROUPED -> {
            val item = viewModel.getGroupedItemList(page).collectAsState(initial = emptyMap())
            return {
                groupedContent(
                    { item.value },
                    thumbnailModifier = thumbnailModifier,
                    titleModifier = titleModifier,
                    onListItemClicked,
                    viewModel::onMenuClicked,
                )
            }
        }
    }
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
    menuItems: Collection<TimetableMenuItem> = TimetableMenuItem.entries,
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
    internal val page: TimetablePage,
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
            return TimetablePage.entries.map { TimetableTabData(it, 0) }
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
        ) { Text("page: ${(it as TimetableTabData).page.name}") }
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
            menuItemsProvider = { TimetableMenuItem.entries },
            onMenuItemClicked = {},
        ) {}
    }
}
