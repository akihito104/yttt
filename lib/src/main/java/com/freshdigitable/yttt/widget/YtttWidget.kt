package com.freshdigitable.yttt.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import dagger.hilt.android.EntryPointAccessors

class YtttWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            YtttWidgetEntryPoint::class.java
        )
        val fetchPinnedVideo = entryPoint.fetchPinnedVideoUseCase()
        val pinned = fetchPinnedVideo().getOrDefault(emptyList())
        provideContent {
            MyContent(pinned)
        }
    }

    @Composable
    private fun MyContent(pinned: List<YouTubeVideoExtended>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            if (pinned.isEmpty()) {
                Text(text = "No pinned videos")
            } else {
                Text(text = "Pinned: ${pinned.size}")
                pinned.forEach {
                    Text(text = it.title)
                }
            }
        }
    }
}
