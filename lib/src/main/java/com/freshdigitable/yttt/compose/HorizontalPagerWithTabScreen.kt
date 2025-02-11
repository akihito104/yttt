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
    Column(modifier = Modifier.fillMaxSize()) {
        val tabData = viewModel.tabData.collectAsState(initial = viewModel.initialTab)
        val pagerState = rememberPagerState { tabData.value.size }
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .wrapContentSize()
                .then(tabModifier),
        ) {
            val coroutineScope = rememberCoroutineScope()
            tabData.value.forEachIndexed { index, data ->
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
            pageContent = { page(tabData.value[it]) },
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
