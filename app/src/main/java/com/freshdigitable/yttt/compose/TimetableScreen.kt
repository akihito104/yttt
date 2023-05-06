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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.google.accompanist.themeadapter.material.MdcTheme
import java.time.Instant

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TimetableScreen(
    refreshingProvider: () -> Boolean,
    onRefresh: () -> Unit,
    listItemProvider: () -> List<LiveVideo>,
    onListItemClicked: (LiveVideo.Id) -> Unit,
) {
    val refreshing = refreshingProvider()
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
            itemsIndexed(
                items = listItemProvider(),
                key = { _, item -> item.id.value }) { _, item ->
                LiveVideoListItemView(video = item) { onListItemClicked(item.id) }
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
        TimetableScreen(
            refreshingProvider = { false },
            listItemProvider = {
                listOf(
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
                )
            }, onRefresh = {}, onListItemClicked = {})
    }
}
