package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.DATE_WEEKDAY_MINUTES
import com.freshdigitable.yttt.data.model.DateFormatterPattern
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.model.toPattern
import java.time.Instant

private val REGEX_LOCAL_DATETIME_TEXT = """^(.*)\s(\d{1,2}):(.*)$""".toRegex()
private val MatchResult.date: String get() = groupValues[1]
private val MatchResult.minute: String get() = groupValues[3]
internal fun Instant.toAdjustedLocalDateTimeText(
    timeAdjustment: TimeAdjustment,
    dateFormatterPattern: DateFormatterPattern = DATE_WEEKDAY_MINUTES,
): String {
    val localDateTime = (this - timeAdjustment.extraHourOfDay).toLocalDateTime(timeAdjustment.zoneId)
    val hour = localDateTime.hour + timeAdjustment.extraHourOfDay.toHours()
    return localDateTime.format(dateFormatterPattern.toPattern(timeAdjustment.locale))
        .replace(REGEX_LOCAL_DATETIME_TEXT) {
            "${it.date} ${hour.toString().padStart(2, '0')}:${it.minute}"
        }
}
