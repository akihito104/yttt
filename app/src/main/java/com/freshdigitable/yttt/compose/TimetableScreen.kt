package com.freshdigitable.yttt.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.freshdigitable.yttt.MainViewModel
import com.freshdigitable.yttt.TimetablePage
import com.freshdigitable.yttt.TimetableTabFragmentDirections
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.google.accompanist.themeadapter.material.MdcTheme
import java.time.Instant

@Composable
fun TimetableScreen(
    page: TimetablePage,
    viewModel: MainViewModel,
    navController: NavController,
) {
    val refreshing = viewModel.isLoading.observeAsState(false).value
    val list = page.bind(viewModel).observeAsState(emptyList()).value
    TimetableScreen(
        refreshing = refreshing,
        list = list,
        onRefresh = viewModel::loadList,
        onClick = { item ->
            val action = TimetableTabFragmentDirections
                .actionTttFragmentToVideoDetailFragment(item.id.value)
            navController.navigate(action)
        },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TimetableScreen(
    refreshing: Boolean,
    list: List<LiveVideo>,
    onRefresh: () -> Unit,
    onClick: (LiveVideo) -> Unit,
) {
    val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items = list, key = { _, item -> item.id.value }) { _, item ->
                LiveVideoListItemView(video = item) { onClick(item) }
            }
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun TimetableScreenPreview() {
    MdcTheme {
        TimetableScreen(false, listOf(
            LiveVideoEntity(
                id = LiveVideo.Id("a"),
                title = "video title",
                thumbnailUrl = "",
                scheduledStartDateTime = Instant.now(),
                channel = LiveChannelEntity(
                    id = LiveChannel.Id("b"),
                    title = "channel title",
                    iconUrl = "",
                ),
            ),
        ), {}, {})
    }
}
