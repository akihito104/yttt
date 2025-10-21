package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.DATE_WEEKDAY
import com.freshdigitable.yttt.data.model.toLocalDateTime
import com.freshdigitable.yttt.data.model.toPattern
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
internal data class GroupKey(
    val key: LocalDate,
) {
    val text: String get() = key.format(FORMATTER)

    companion object {
        private val FORMATTER = DATE_WEEKDAY.toPattern()
        internal fun create(
            scheduledStartDateTime: Instant,
            extraHourOfDay: Duration,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): GroupKey = GroupKey((scheduledStartDateTime - extraHourOfDay).toLocalDateTime(zoneId).toLocalDate())
    }
}
