package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.freshdigitable.yttt.MainViewModel
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.data.model.LiveVideo
import com.google.accompanist.themeadapter.material.MdcTheme
import kotlinx.coroutines.launch

@Composable
fun TimetableTabScreen(
    viewModel: MainViewModel,
    onListItemClicked: (LiveVideo.Id) -> Unit,
) {
    val tabData = TimetablePage.values().map { p ->
        val count = p.bind(viewModel)
            .map { it.size }
            .distinctUntilChanged()
            .observeAsState(0).value
        TabData(p, count)
    }
    TimetableTabScreen(
        tabData = tabData,
    ) { index ->
        TimetableScreen(
            page = TimetablePage.values()[index],
            viewModel = viewModel,
            onListItemClicked = onListItemClicked,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimetableTabScreen(
    tabData: List<TabData>,
    page: @Composable (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.wrapContentSize(),
        ) {
            tabData.sortedBy { it.index }.forEachIndexed { index, data ->
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
            pageCount = tabData.size,
            state = pagerState,
            pageContent = page,
        )
    }
}

@Immutable
private class TabData(
    private val page: TimetablePage,
    private val count: Int
) {
    val index: Int = page.ordinal

    @Composable
    @ReadOnlyComposable
    fun text(): String = stringResource(id = page.textRes, count)
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TimetableTabScreenPreview() {
    MdcTheme {
        TimetableTabScreen(
            tabData = listOf(
                TabData(TimetablePage.OnAir, 10),
                TabData(TimetablePage.Upcoming, 3),
            ),
        ) { Text("page: $it") }
    }
}
