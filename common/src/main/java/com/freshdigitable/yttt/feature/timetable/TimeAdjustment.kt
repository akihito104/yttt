package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Duration

@Immutable
data class TimeAdjustment(
    val extraHourOfDay: Duration,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val locale: Locale = Locale.getDefault(),
) {
    companion object {
        fun zero(): TimeAdjustment = TimeAdjustment(Duration.ZERO)
    }
}
