package com.freshdigitable.yttt.feature.channel

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import com.freshdigitable.yttt.data.model.AnnotatableString

internal object TwitchChannelDetailPageComposableFactory : ChannelDetailPageComposableFactory {
    override fun create(tab: ChannelDetailPageTab<*>): ChannelDetailPageComposable =
        when (tab as TwitchChannelDetailTab) {
            TwitchChannelDetailTab.About -> {
                {
                    val text = pagerContent.annotatedDetail
                        .collectAsState(initial = AnnotatableString.empty())
                    annotatedText { text.value }()
                }
            }

            TwitchChannelDetailTab.Vod -> {
                {
                    val p = pagerContent as TwitchChannelDetailPagerContent
                    val vod = p.vod.collectAsState(initial = emptyList())
                    list(
                        itemProvider = { vod.value },
                        idProvider = { it.id },
                    ) { videoItem(it.getThumbnailUrl(), it.title)() }()
                }
            }

            TwitchChannelDetailTab.Debug -> {
                {
                    val detail = (pagerContent as TwitchChannelDetailPagerContent).debug
                        .collectAsState(initial = emptyMap())
                    list(
                        itemProvider = { detail.value.entries.toList() },
                        idProvider = { it.key },
                    ) { Text(text = it.value) }()
                }
            }
        }
}
