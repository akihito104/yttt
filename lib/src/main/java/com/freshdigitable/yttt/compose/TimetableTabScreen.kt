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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.compose.preview.LightModePreview
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import com.freshdigitable.yttt.feature.timetable.TimetablePage
import com.freshdigitable.yttt.feature.timetable.TimetableTabViewModel
import com.freshdigitable.yttt.feature.timetable.textRes
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    AppLogger.logD("TimetableTab") { "start:" }
    LaunchedEffect(Unit) {
        if (viewModel.canUpdate) {
            viewModel.loadList()
        }
    }
    val refreshing = viewModel.isLoading.observeAsState(false)
    val listState = TimetablePage.entries.associateWith { rememberLazyListState() }
    val timetableContent = TimetablePage.entries.associateWith {
        timetableContent(
            page = it,
            thumbnailModifier = thumbnailModifier,
            titleModifier = titleModifier,
            onListItemClicked = onListItemClicked,
            viewModel = viewModel,
        )
    }
    HorizontalPagerWithTabScreen(
        tabModifier = tabModifier,
        viewModel = viewModel,
    ) { tab ->
        TimetableScreen(
            lazyListState = checkNotNull(listState[tab.page]),
            refreshingProvider = { refreshing.value },
            onRefresh = viewModel::loadList,
            listContent = checkNotNull(timetableContent[tab.page]),
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
) : TabData<TimetableTabData> {
    @Composable
    @ReadOnlyComposable
    override fun title(): String = stringResource(id = page.textRes, count)
    override fun compareTo(other: TimetableTabData): Int = page.ordinal - other.page.ordinal

    companion object {
        fun initialValues(): List<TimetableTabData> {
            return TimetablePage.entries.map { TimetableTabData(it, 0) }
        }
    }
}

@LightModePreview
@Composable
private fun TimetableTabScreenPreview() {
    val tabs = listOf(
        TimetableTabData(TimetablePage.OnAir, 10),
        TimetableTabData(TimetablePage.Upcoming, 3),
        TimetableTabData(TimetablePage.FreeChat, 7),
    )
    AppTheme {
        HorizontalPagerWithTabScreen(
            viewModel = object : HorizontalPagerTabViewModel<TimetableTabData> {
                override val tabData: Flow<List<TimetableTabData>> get() = flowOf(tabs)
                override val initialTab: List<TimetableTabData> get() = tabs
            },
        ) { Text("page: ${it.page.name}") }
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
