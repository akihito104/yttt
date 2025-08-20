package com.freshdigitable.yttt.feature.timetable

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

class GroupKeyTest : ShouldSpec({
    val jstZone = ZoneId.of("Asia/Tokyo")

    fun entity(scheduledStartDateTime: Instant, offset: Int): GroupKey =
        GroupKey.create(scheduledStartDateTime, Duration.ofHours(offset.toLong()), jstZone)

    should("offset 2 hours, time is just before 2 AM, returns previous date") {
        // setup
        val sut = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            offset = 2,
        )
        // exercise
        val actual = sut.key
        // verify
        actual shouldBe LocalDate.of(2025, Month.JANUARY, 22)
    }

    should("offset 2 hours, time is exactly 2 AM, returns same date") {
        // setup
        val sut = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00"),
            offset = 2,
        )
        // exercise
        val actual = sut.key
        // verify
        actual shouldBe LocalDate.of(2025, Month.JANUARY, 23)
    }
})
