package com.freshdigitable.yttt.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalPagerWithTabScreen(
    tabModifier: Modifier = Modifier,
    tabDataProvider: () -> List<TabData>,
    page: @Composable (TabData) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val tabData = tabDataProvider()
        val pagerState = rememberPagerState { tabData.size }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .wrapContentSize()
                .then(tabModifier),
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
                    text = { Text(text = data.title()) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            pageContent = { page(tabData[it]) },
        )
    }
}

interface TabData : Comparable<TabData> {
    @Composable
    fun title(): String
    override fun compareTo(other: TabData): Int
}
