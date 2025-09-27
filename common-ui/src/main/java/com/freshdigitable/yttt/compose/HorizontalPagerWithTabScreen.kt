package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.freshdigitable.yttt.AppLogger
import com.freshdigitable.yttt.logD
import kotlinx.coroutines.launch

@Composable
fun <T : TabData<T>> HorizontalPagerWithTabScreen(
    tabProvider: HorizontalPagerTabDataProvider<T>,
    modifier: Modifier = Modifier,
    tabModifier: Modifier = Modifier,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    page: @Composable PagerScope.(T) -> Unit,
) {
    AppLogger.logD("HorizontalPager(VM)") { "start:" }
    HorizontalPagerWithTabScreen(
        tabCount = tabProvider().size,
        tab = { tabProvider()[it].title() },
        modifier = modifier,
        tabModifier = tabModifier,
        edgePadding = edgePadding,
        page = { page(tabProvider()[it]) },
    )
}

@Composable
fun HorizontalPagerWithTabScreen(
    tabCount: Int,
    tab: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    tabModifier: Modifier = Modifier,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    page: @Composable PagerScope.(Int) -> Unit,
) {
    AppLogger.logD("HorizontalPager") { "start:" }
    Column(modifier = modifier.fillMaxSize()) {
        val pagerState = rememberPagerState { tabCount }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = edgePadding,
            modifier = Modifier
                .wrapContentSize()
                .then(tabModifier),
        ) {
            val coroutineScope = rememberCoroutineScope()
            for (index in 0..<tabCount) {
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = tab(index)) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            pageContent = { page(it) },
        )
    }
}

typealias HorizontalPagerTabDataProvider<T> = () -> List<T>
