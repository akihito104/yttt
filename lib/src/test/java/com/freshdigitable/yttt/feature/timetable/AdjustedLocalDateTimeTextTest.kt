package com.freshdigitable.yttt.feature.timetable

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class AdjustedLocalDateTimeTextTest : ShouldSpec(
    {
        should("JP_returnsCurrentDate") {
            // setup
            val timeAdjustment = TimeAdjustment(
                extraHourOfDay = 2.hours,
                timeZone = TimeZone.of("Asia/Tokyo"),
                locale = Locale.JAPAN,
            )
            // exercise
            val actual = Instant.parse("2025-01-23T02:00:00.000+09:00").toAdjustedLocalDateTimeText(timeAdjustment)
            // verify
            actual shouldBe "2025/01/23(木) 02:00"
        }

        should("JP_returnsYesterdayDate") {
            // setup
            val timeAdjustment = TimeAdjustment(
                extraHourOfDay = 2.hours,
                timeZone = TimeZone.of("Asia/Tokyo"),
                locale = Locale.JAPAN,
            )
            // exercise
            val actual = (Instant.parse("2025-01-23T02:00:00.000+09:00") - 1.milliseconds)
                .toAdjustedLocalDateTimeText(timeAdjustment)
            // verify
            actual shouldBe "2025/01/22(水) 25:59"
        }

        should("US") {
            // setup
            val timeAdjustment = TimeAdjustment(
                extraHourOfDay = 2.hours,
                timeZone = TimeZone.of("America/New_York"),
                locale = Locale.US,
            )
            // exercise
            val actual = Instant.parse("2025-01-23T00:00:00.000-05:00").toAdjustedLocalDateTimeText(timeAdjustment)
            // verify
            actual shouldBe "2025/01/22(Wed) 24:00"
        }
    },
)
