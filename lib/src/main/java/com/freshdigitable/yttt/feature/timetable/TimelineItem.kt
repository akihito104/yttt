package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Immutable
data class TimelineItem(
    private val video: LiveVideo<*>,
    private val extraHourOfDay: Duration,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    val id: LiveVideo.Id
        get() = video.id
    val thumbnail: LiveVideoThumbnail
        get() = video
    val title: String
        get() = video.title
    val channel: LiveChannel
        get() = video.channel
    private val dateTimeToDisplay: Instant
        get() = when (video) {
            is LiveVideo.OnAir -> video.actualStartDateTime
            is LiveVideo.Upcoming -> video.scheduledStartDateTime
            is LiveVideo.FreeChat -> video.scheduledStartDateTime
            else -> error("unsupported LiveVideo type: ${video.javaClass.name}")
        }
    val localDateTimeText: String
        get() {
            val localDateTime = (dateTimeToDisplay - extraHourOfDay).toLocalDateTime(zoneId)
            val hour = localDateTime.hour + extraHourOfDay.toHours()
            return localDateTime.format(dateTimeFormatter(locale))
                .replace(REGEX_LOCAL_DATETIME_TEXT) {
                    "${it.groupValues[1]} ${hour.toString().padStart(2, '0')}:${it.groupValues[3]}"
                }
        }
    internal val groupKey: GroupKey?
        get() = when (video) {
            is LiveVideo.Upcoming -> GroupKey.create(
                video.scheduledStartDateTime,
                extraHourOfDay,
                zoneId,
            )

            else -> null
        }

    companion object {
        private val REGEX_LOCAL_DATETIME_TEXT = """^(.*)\s(\d{1,2}):(.*)$""".toRegex()
    }
}

internal data class GroupKey(
    val key: LocalDate,
) {
    val text: String
        get() = key.format(dateWeekdayFormatter)

    companion object {
        internal fun create(
            scheduledStartDateTime: Instant,
            extraHourOfDay: Duration,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): GroupKey = GroupKey(
            (scheduledStartDateTime - extraHourOfDay).toLocalDateTime(zoneId).toLocalDate()
        )
    }
}
