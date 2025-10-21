package com.freshdigitable.yttt.feature.timetable

import androidx.compose.runtime.Immutable
import java.time.Duration
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
