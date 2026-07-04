package com.freshdigitable.yttt.feature.timetable

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class GroupKeyTest : ShouldSpec(
    {
        val jstZone = TimeZone.of("Asia/Tokyo")

        fun entity(scheduledStartDateTime: Instant, offset: Int): GroupKey =
            GroupKey.create(scheduledStartDateTime, offset.toLong().hours, jstZone)

        should("offset 2 hours, time is just before 2 AM, returns previous date") {
            // setup
            val sut = entity(
                scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00") - 1.milliseconds,
                offset = 2,
            )
            // exercise
            val actual = sut.key
            // verify
            actual shouldBe LocalDate(2025, Month.JANUARY, 22)
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
            actual shouldBe LocalDate(2025, Month.JANUARY, 23)
        }
    },
)
