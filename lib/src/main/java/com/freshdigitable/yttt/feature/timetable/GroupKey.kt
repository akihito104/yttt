package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.dateWeekdayFormatter
import com.freshdigitable.yttt.data.model.toLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
internal data class GroupKey(
    val key: LocalDate,
) {
    val text: String get() = key.format(dateWeekdayFormatter)

    companion object {
        internal fun create(
            scheduledStartDateTime: Instant,
            extraHourOfDay: Duration,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): GroupKey = GroupKey((scheduledStartDateTime - extraHourOfDay).toLocalDateTime(zoneId).toLocalDate())
    }
}
