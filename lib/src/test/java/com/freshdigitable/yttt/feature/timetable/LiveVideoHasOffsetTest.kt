package com.freshdigitable.yttt.feature.timetable

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

class LiveVideoHasOffsetTest {
    private val jstZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsPreviousDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            offset = 2,
        )
        // exercise
        val actual = video.key
        // verify
        assertThat(actual).isEqualTo(LocalDate.of(2025, Month.JANUARY, 22))
    }

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsSameDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00"),
            offset = 2,
        )
        // exercise
        val actual = video.key
        // verify
        assertThat(actual).isEqualTo(LocalDate.of(2025, Month.JANUARY, 23))
    }

    private fun entity(scheduledStartDateTime: Instant, offset: Int): GroupKey =
        GroupKey.create(scheduledStartDateTime, Duration.ofHours(offset.toLong()), jstZone)
}
