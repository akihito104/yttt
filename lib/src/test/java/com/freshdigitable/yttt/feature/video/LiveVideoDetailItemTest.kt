package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class StatusTextForOnAirParam(
    val actualStartDateTime: Instant,
    val viewerCount: BigInteger?,
    val expected: String,
    val name: String,
)

internal data class StatusTextForUpcomingParam(
    val scheduledStartDateTime: Instant,
    val viewerCount: BigInteger?,
    val expected: String,
    val name: String,
)

class LiveVideoDetailItemTest : ShouldSpec({
    val commonZoneId = ZoneId.of("Asia/Tokyo")
    val commonLocale = Locale.JAPAN

    context("statsText for OnAir video") {
        val params = listOf(
            StatusTextForOnAirParam(
                actualStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                viewerCount = BigInteger.ZERO,
                expected = "2025/01/12(日) 19:00:00・Viewers:0",
                name = "with viewers"
            ),
            StatusTextForOnAirParam(
                actualStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                viewerCount = null,
                expected = "2025/01/12(日) 19:00:00",
                name = "without viewers"
            ),
        )
        withData(
            nameFn = { param -> "given ${param.name}, then actual string is '''Started:${param.expected}'''" },
            params
        ) { param ->
            // setup
            val sut = LiveVideoDetailItem(
                video = mockk<LiveVideo.OnAir>().apply {
                    every { actualStartDateTime } returns param.actualStartDateTime
                    every { viewerCount } returns param.viewerCount
                },
                annotatableTitle = AnnotatableString.empty(),
                annotatableDescription = AnnotatableString.empty(),
                zoneId = commonZoneId,
                locale = commonLocale,
            )
            // exercise
            val actual = sut.statsText
            // verify
            actual shouldBe "Started:${param.expected}"
        }
    }

    context("statsText for Upcoming video") {
        val params = listOf(
            StatusTextForUpcomingParam(
                scheduledStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                viewerCount = BigInteger.ZERO,
                expected = "2025/01/12(日) 19:00・Viewers:0",
                name = "with viewers"
            ),
            StatusTextForUpcomingParam(
                scheduledStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                viewerCount = null,
                expected = "2025/01/12(日) 19:00",
                name = "without viewers"
            ),
        )
        withData(
            nameFn = { param -> "given ${param.name}, then actual string is '''Starting:${param.expected}'''" },
            params
        ) { param ->
            // setup
            val sut = LiveVideoDetailItem(
                video = mockk<LiveVideo.Upcoming>().apply {
                    every { scheduledStartDateTime } returns param.scheduledStartDateTime
                    every { viewerCount } returns param.viewerCount
                },
                annotatableTitle = AnnotatableString.empty(),
                annotatableDescription = AnnotatableString.empty(),
                zoneId = commonZoneId,
                locale = commonLocale,
            )
            // exercise
            val actual = sut.statsText
            // verify
            actual shouldBe "Starting:${param.expected}"
        }
    }
})
