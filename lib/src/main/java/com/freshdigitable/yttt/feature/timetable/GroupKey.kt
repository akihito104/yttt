package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import com.freshdigitable.yttt.data.model.DATE_WEEKDAY
import com.freshdigitable.yttt.data.model.toLocalFormattedText
import com.freshdigitable.yttt.data.model.toPattern
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

@Immutable
internal data class GroupKey(
    val key: LocalDate,
) {
    val text: String get() = key.toLocalFormattedText(FORMATTER)

    companion object {
        private val FORMATTER = DATE_WEEKDAY.toPattern()
        internal fun create(
            scheduledStartDateTime: Instant,
            extraHourOfDay: Duration,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
        ): GroupKey = GroupKey((scheduledStartDateTime - extraHourOfDay).toLocalDateTime(timeZone).date)
    }
}
