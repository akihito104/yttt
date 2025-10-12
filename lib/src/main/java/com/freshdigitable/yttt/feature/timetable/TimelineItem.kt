package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

sealed class TimelineItem(
    video: LiveTimelineItem,
    timeAdjustment: TimeAdjustment,
) : LiveTimelineItem by video {
    val thumbnail: LiveVideoThumbnail get() = this
    val localDateTimeText: String = video.dateTime.toAdjustedLocalDateTimeText(timeAdjustment)

    @Immutable
    data class Simple(val video: LiveTimelineItem, val timeAdjustment: TimeAdjustment) :
        TimelineItem(video, timeAdjustment)

    @Immutable
    data class Grouped(val video: LiveTimelineItem, val timeAdjustment: TimeAdjustment) :
        TimelineItem(video, timeAdjustment) {
        internal val groupKey: GroupKey? = GroupKey.create(
            video.dateTime,
            timeAdjustment.extraHourOfDay,
            timeAdjustment.zoneId,
        )
    }

    companion object {
        private val REGEX_LOCAL_DATETIME_TEXT = """^(.*)\s(\d{1,2}):(.*)$""".toRegex()
        fun Instant.toAdjustedLocalDateTimeText(timeAdjustment: TimeAdjustment): String {
            val localDateTime = (this - timeAdjustment.extraHourOfDay).toLocalDateTime(timeAdjustment.zoneId)
            val hour = localDateTime.hour + timeAdjustment.extraHourOfDay.toHours()
            return localDateTime.format(dateTimeFormatter(timeAdjustment.locale))
                .replace(REGEX_LOCAL_DATETIME_TEXT) {
                    "${it.groupValues[1]} ${hour.toString().padStart(2, '0')}:${it.groupValues[3]}"
                }
        }
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
            (scheduledStartDateTime - extraHourOfDay).toLocalDateTime(zoneId).toLocalDate(),
        )
    }
}

data class TimeAdjustment(
    val extraHourOfDay: Duration,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
)
