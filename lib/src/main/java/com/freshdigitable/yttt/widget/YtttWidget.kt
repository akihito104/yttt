package com.freshdigitable.yttt.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import dagger.hilt.android.EntryPointAccessors

class YtttWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            YtttWidgetEntryPoint::class.java,
        )
        val fetchPinnedVideo = entryPoint.fetchPinnedVideoUseCase()
        val pinned = fetchPinnedVideo().getOrDefault(emptyList())
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val videoId = prefs[videoIdKey]

        val video = if (pinned.isNotEmpty()) {
            val current = pinned.firstOrNull { it.id.value == videoId }
            if (current != null) {
                current
            } else {
                val next = pinned.firstOrNull { it.id.value > (videoId ?: "") }
                next ?: pinned.first()
            }
        } else null

        val bitmap = video?.let { loadBitmap(context, it.thumbnailUrl) }

        provideContent {
            MyContent(pinned, video, bitmap)
        }
    }

    @Composable
    private fun MyContent(
        pinned: List<YouTubeVideoExtended>,
        video: YouTubeVideoExtended?,
        bitmap: Bitmap?
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            if (pinned.isEmpty() || video == null) {
                Text(text = "No pinned videos")
            } else {
                Box(
                    modifier = GlanceModifier.clickable(
                        actionRunCallback<SwitchAction>(
                            parameters = actionParametersOf(SwitchAction.videoIdKey to video.id.value)
                        )
                    )
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = video.title,
                            modifier = GlanceModifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(text = video.title)
                    }
                }
            }
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()
        val result = loader.execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    companion object {
        internal val videoIdKey = stringPreferencesKey("pinned_video_id")
    }
}

class SwitchAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val currentVideoId = parameters[videoIdKey]
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            YtttWidgetEntryPoint::class.java,
        )
        val fetchPinnedVideo = entryPoint.fetchPinnedVideoUseCase()
        val pinned = fetchPinnedVideo().getOrDefault(emptyList())
        if (pinned.isEmpty()) return

        val nextVideo = if (currentVideoId != null) {
            val next = pinned.firstOrNull { it.id.value > currentVideoId }
            next ?: pinned.first()
        } else {
            pinned.first()
        }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[YtttWidget.videoIdKey] = nextVideo.id.value
            }
        }
        YtttWidget().update(context, glanceId)
    }

    companion object {
        internal val videoIdKey = ActionParameters.Key<String>("pinned_video_id")
    }
}
