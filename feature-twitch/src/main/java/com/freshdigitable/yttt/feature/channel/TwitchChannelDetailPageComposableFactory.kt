package com.freshdigitable.yttt.feature.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freshdigitable.yttt.data.model.AnnotatableString

internal object TwitchChannelDetailPageComposableFactory : ChannelDetailPageComposableFactory {
    override fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable {
        when (tab as TwitchChannelDetailTab) {
            TwitchChannelDetailTab.About -> return {
                val text = delegate.pagerContent.annotatedDetail
                    .collectAsState(initial = AnnotatableString.empty())
                annotatedText { text.value }()
            }

            TwitchChannelDetailTab.Vod -> return {
                val p = delegate.pagerContent as TwitchChannelDetailPagerContent
                val vod = p.vod.collectAsState(initial = emptyList())
                list(itemProvider = { vod.value }, idProvider = { it.id }) {
                    videoItem(it.getThumbnailUrl(), it.title)()
                }()
            }

            TwitchChannelDetailTab.Debug -> return {
                val detail = delegate.channelDetailBody.collectAsState(initial = null)
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = detail.value?.toString() ?: "",
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}
