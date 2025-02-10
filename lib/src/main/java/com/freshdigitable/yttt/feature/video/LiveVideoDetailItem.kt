package com.freshdigitable.yttt.feature.video

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateTimeSecondFormatter
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import java.time.ZoneId
import java.util.Locale

@Immutable
internal data class LiveVideoDetailItem(
    private val video: LiveVideo<*>,
    val annotatableDescription: AnnotatableString,
    val annotatableTitle: AnnotatableString,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    val thumbnail: LiveVideoThumbnail get() = video
    val channel: LiveChannel get() = video.channel
    val statsText: String
        get() {
            val time = when (video) {
                is LiveVideo.OnAir -> "Started:${video.statsDateTime(zoneId, locale)}"
                is LiveVideo.Upcoming -> "Starting:${video.statsDateTime(zoneId, locale)}"
                else -> null
            }
            val viewerCount = video.viewerCount
            val count = if (viewerCount != null) "Viewers:$viewerCount" else null
            return listOfNotNull(time, count).joinToString("・")
        }

    companion object {
        private fun LiveVideo.OnAir.statsDateTime(zoneId: ZoneId, locale: Locale): String =
            actualStartDateTime.toLocalFormattedText(dateTimeSecondFormatter(locale), zoneId)

        private fun LiveVideo.Upcoming.statsDateTime(zoneId: ZoneId, locale: Locale): String =
            scheduledStartDateTime.toLocalFormattedText(dateTimeFormatter(locale), zoneId)
    }
}
