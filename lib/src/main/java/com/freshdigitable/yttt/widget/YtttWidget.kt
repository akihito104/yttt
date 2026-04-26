package com.freshdigitable.yttt.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.lib.R
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
        bitmap: Bitmap?,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if ((pinned.isEmpty()) || (video == null)) {
                Text(text = "No pinned videos")
            } else {
                Box(
                    modifier = GlanceModifier.fillMaxSize().clickable(
                        actionStartActivity(Intent(Intent.ACTION_VIEW, video.url.toUri())),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = video.title,
                            modifier = GlanceModifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(text = video.title)
                    }
                }
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    CaretButton(
                        iconRes = R.drawable.chevron_left,
                        contentDescription = "Previous",
                        action = actionRunCallback<SwitchAction>(
                            parameters = actionParametersOf(
                                SwitchAction.videoIdKey to video.id.value,
                                SwitchAction.directionKey to "prev",
                            ),
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    CaretButton(
                        iconRes = R.drawable.chevron_right,
                        contentDescription = "Next",
                        action = actionRunCallback<SwitchAction>(
                            parameters = actionParametersOf(
                                SwitchAction.videoIdKey to video.id.value,
                                SwitchAction.directionKey to "next",
                            ),
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun CaretButton(
        iconRes: Int,
        contentDescription: String,
        action: androidx.glance.action.Action,
    ) {
        Box(
            modifier = GlanceModifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.circle_background),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size(24.dp).clickable(action),
            )
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()
        val result = loader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        return bitmap?.let { ImageLoadableView.cropLetterbox(it) }
    }

    companion object {
        internal val videoIdKey = stringPreferencesKey("pinned_video_id")
    }
}

class SwitchAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val currentVideoId = parameters[videoIdKey]
        val direction = parameters[directionKey]
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            YtttWidgetEntryPoint::class.java,
        )
        val fetchPinnedVideo = entryPoint.fetchPinnedVideoUseCase()
        val pinned = fetchPinnedVideo().getOrDefault(emptyList())
        if (pinned.isEmpty()) return

        val nextVideo = if (currentVideoId != null) {
            if (direction == "prev") {
                val prev = pinned.lastOrNull { it.id.value < currentVideoId }
                prev ?: pinned.last()
            } else {
                val next = pinned.firstOrNull { it.id.value > currentVideoId }
                next ?: pinned.first()
            }
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
        internal val directionKey = ActionParameters.Key<String>("direction")
    }
}
