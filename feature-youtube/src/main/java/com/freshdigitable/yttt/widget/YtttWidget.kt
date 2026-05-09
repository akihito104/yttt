package com.freshdigitable.yttt.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
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
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.url
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.feature.timetable.youtube.R
import com.freshdigitable.yttt.feature.video.FetchPinnedVideoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class YtttWidget : GlanceAppWidget() {
    override val stateDefinition: StorableStateDefinition = StorableStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stateFactory = context.widgetEntryPoint().stateFactory()
        val state = stateFactory.create(stateDefinition.flowVideoId(context, id)) {
            stateDefinition.updateVideoId(context, id, it.value)
        }
        val image = state.pinnedItem.map { v -> v?.let { loadBitmap(context, it.thumbnailUrl) } }

        provideContent {
            val video by state.pinnedItem.collectAsState(null)
            val bitmap by image.collectAsState(null)
            val prevVideoId by state.prevItem.collectAsState(null)
            val nextVideoId by state.nextItem.collectAsState(null)
            MyContent(video, bitmap, prevVideoId, nextVideoId)
        }
    }

    @Composable
    private fun MyContent(
        video: YouTubeVideoExtended?,
        bitmap: Bitmap?,
        prevVideoId: YouTubeVideo.Id?,
        nextVideoId: YouTubeVideo.Id?,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (video == null) {
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
                    if (prevVideoId != null) {
                        CaretButton(
                            iconRes = R.drawable.chevron_left,
                            contentDescription = "Previous",
                            action = SwitchAction.create(prevVideoId),
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (nextVideoId != null) {
                        CaretButton(
                            iconRes = R.drawable.chevron_right,
                            contentDescription = "Next",
                            action = SwitchAction.create(nextVideoId),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CaretButton(
        @DrawableRes iconRes: Int,
        contentDescription: String,
        action: Action,
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
        internal suspend fun update(context: Context, glanceId: GlanceId, videoId: String) {
            StorableStateDefinition.updateVideoId(context, glanceId, videoId)
            YtttWidget().update(context, glanceId)
        }
    }
}

object StorableStateDefinition : GlanceStateDefinition<Preferences> by PreferencesGlanceStateDefinition {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> {
        return stores.getOrPut(fileKey) {
            PreferencesGlanceStateDefinition.getDataStore(context, fileKey)
        }
    }

    private val videoIdKey = stringPreferencesKey("pinned_video_id")
    internal suspend fun flowVideoId(context: Context, glanceId: GlanceId): Flow<String?> {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        return getDataStore(context, "appWidget-$appWidgetId")
            .data.map { it[videoIdKey] }.distinctUntilChanged()
    }

    internal suspend fun updateVideoId(context: Context, glanceId: GlanceId, videoId: String) {
        updateAppWidgetState(context, this, glanceId) { prefs ->
            prefs.toMutablePreferences()
                .apply { this[videoIdKey] = videoId }
        }
    }
}

internal class SwitchAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val nextVideoId = requireNotNull(parameters[videoIdKey])
        YtttWidget.update(context, glanceId, nextVideoId)
    }

    companion object {
        private val videoIdKey = ActionParameters.Key<String>("video_id")
        internal fun create(videoId: YouTubeVideo.Id): Action = actionRunCallback<SwitchAction>(
            actionParametersOf(videoIdKey to videoId.value),
        )
    }
}

private fun Context.widgetEntryPoint(): YtttWidgetEntryPoint =
    EntryPointAccessors.fromApplication(this, YtttWidgetEntryPoint::class.java)

internal class YtttState @AssistedInject constructor(
    @Assisted private val videoId: Flow<String?>,
    @Assisted private val initialUpdateVideoIdIfNeed: suspend (YouTubeVideo.Id) -> Unit,
    fetchPinnedVideo: FetchPinnedVideoUseCase,
) {
    private val pinned = fetchPinnedVideo()
    val pinnedItem = videoId.combine(pinned) { v, p ->
        if (p.isEmpty()) {
            null
        } else {
            v?.let { p.findById(it) ?: p.getNextById(it) }
                ?: p.first().also { initialUpdateVideoIdIfNeed(it.id) }
        }
    }
    val prevItem = videoId.combine(pinned.filter { it.isNotEmpty() }) { v, p ->
        v?.let { p.getPrevById(it) } ?: p.firstOrNull()
    }.map { it?.id }
    val nextItem = videoId.combine(pinned.filter { it.isNotEmpty() }) { v, p ->
        v?.let { p.getNextById(it) } ?: p.firstOrNull()
    }.map { it?.id }

    companion object {
        private fun List<YouTubeVideoExtended>.findById(videoId: String): YouTubeVideoExtended? =
            this.firstOrNull { it.id.value == videoId }

        private fun List<YouTubeVideoExtended>.getNextById(videoId: String): YouTubeVideoExtended =
            this.firstOrNull { it.id.value > videoId } ?: this.first()

        private fun List<YouTubeVideoExtended>.getPrevById(videoId: String): YouTubeVideoExtended =
            this.lastOrNull { it.id.value < videoId } ?: this.last()
    }

    @AssistedFactory
    interface Factory {
        fun create(
            videoId: Flow<String?>,
            initialUpdateVideoIdIfNeed: suspend (YouTubeVideo.Id) -> Unit,
        ): YtttState
    }
}
