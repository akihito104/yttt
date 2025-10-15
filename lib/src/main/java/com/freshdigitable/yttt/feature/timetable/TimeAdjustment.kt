package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.dateTimeFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Immutable
data class TimeAdjustment(
    val extraHourOfDay: Duration,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
) {
    companion object {
        fun zero(): TimeAdjustment = TimeAdjustment(Duration.ZERO)
    }
}

private val REGEX_LOCAL_DATETIME_TEXT = """^(.*)\s(\d{1,2}):(.*)$""".toRegex()
private val MatchResult.date: String get() = groupValues[1]
private val MatchResult.minute: String get() = groupValues[3]
internal fun Instant.toAdjustedLocalDateTimeText(timeAdjustment: TimeAdjustment): String {
    val localDateTime = (this - timeAdjustment.extraHourOfDay).toLocalDateTime(timeAdjustment.zoneId)
    val hour = localDateTime.hour + timeAdjustment.extraHourOfDay.toHours()
    return localDateTime.format(dateTimeFormatter(timeAdjustment.locale))
        .replace(REGEX_LOCAL_DATETIME_TEXT) {
            "${it.date} ${hour.toString().padStart(2, '0')}:${it.minute}"
        }
}
