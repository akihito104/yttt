package com.freshdigitable.yttt.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun <T : TabData<T>> HorizontalPagerWithTabScreen(
    tabModifier: Modifier = Modifier,
    viewModel: HorizontalPagerTabViewModel<T>,
    page: @Composable (T) -> Unit,
) {
    val tab = viewModel.tabData.collectAsState(initial = viewModel.initialTab)
    HorizontalPagerWithTabScreen(
        tabModifier = tabModifier,
        tabCount = tab.value.size,
        tab = { tab.value[it].title() },
        page = { page(tab.value[it]) },
    )
}

@Composable
fun HorizontalPagerWithTabScreen(
    tabModifier: Modifier = Modifier,
    tabCount: Int,
    tab: @Composable (Int) -> String,
    page: @Composable (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState { tabCount }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
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
                    text = { Text(text = tab(index)) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            pageContent = { page(it) },
        )
    }
}

interface TabData<T : TabData<T>> : Comparable<T> {
    @Composable
    fun title(): String
    override fun compareTo(other: T): Int
}

interface HorizontalPagerTabViewModel<T : TabData<T>> {
    val tabData: Flow<List<T>>
    val initialTab: List<T>
}
