package com.freshdigitable.yttt.feature.timetable

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TimelineItemTest : ShouldSpec(
    {
        should("JP_returnsCurrentDate") {
            // setup
            val timeAdjustment = TimeAdjustment(
                extraHourOfDay = Duration.ofHours(2),
                zoneId = ZoneId.of("Asia/Tokyo"),
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
                extraHourOfDay = Duration.ofHours(2),
                zoneId = ZoneId.of("Asia/Tokyo"),
                locale = Locale.JAPAN,
            )
            // exercise
            val actual = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1)
                .toAdjustedLocalDateTimeText(timeAdjustment)
            // verify
            actual shouldBe "2025/01/22(水) 25:59"
        }

        should("US") {
            // setup
            val timeAdjustment = TimeAdjustment(
                extraHourOfDay = Duration.ofHours(2),
                zoneId = ZoneId.of("America/New_York"),
                locale = Locale.US,
            )
            // exercise
            val actual = Instant.parse("2025-01-23T00:00:00.000-05:00").toAdjustedLocalDateTimeText(timeAdjustment)
            // verify
            actual shouldBe "2025/01/22(Wed) 24:00"
        }
    },
)
