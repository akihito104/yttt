package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(Enclosed::class)
class LiveVideoDetailItemTest {
    @RunWith(Parameterized::class)
    internal class StatusTextForOnAir(private val param: TestParam) {
        internal companion object {
            @Parameters
            @JvmStatic
            fun params(): List<TestParam> = listOf(
                TestParam(
                    actualStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = BigInteger.ZERO,
                    actual = "2025/01/12(日) 19:00:00・Viewers:0",
                ),
                TestParam(
                    actualStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = null,
                    actual = "2025/01/12(日) 19:00:00",
                ),
            )
        }

        @Test
        fun test() {
            // setup
            val sut = LiveVideoDetailItem(
                video = mockk<LiveVideo.OnAir>().apply {
                    every { actualStartDateTime } returns param.actualStartDateTime
                    every { viewerCount } returns param.viewerCount
                },
                annotatableTitle = AnnotatableString.empty(),
                annotatableDescription = AnnotatableString.empty(),
                zoneId = ZoneId.of("Asia/Tokyo"),
                locale = Locale.JAPAN,
            )
            // exercise
            val actual = sut.statsText
            // verify
            assertThat(actual).isEqualTo("Started:${param.actual}")
        }

        internal data class TestParam(
            val actualStartDateTime: Instant,
            val viewerCount: BigInteger?,
            val actual: String,
        )
    }

    @RunWith(Parameterized::class)
    internal class StatusTextForUpcoming(private val param: TestParam) {
        internal companion object {
            @Parameters
            @JvmStatic
            fun params(): List<TestParam> = listOf(
                TestParam(
                    scheduledStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = BigInteger.ZERO,
                    actual = "2025/01/12(日) 19:00・Viewers:0",
                ),
                TestParam(
                    scheduledStartDateTime = Instant.parse("2025-01-12T19:00:00.000+09:00"),
                    viewerCount = null,
                    actual = "2025/01/12(日) 19:00",
                ),
            )
        }

        @Test
        fun test() {
            // setup
            val sut = LiveVideoDetailItem(
                video = mockk<LiveVideo.Upcoming>().apply {
                    every { scheduledStartDateTime } returns param.scheduledStartDateTime
                    every { viewerCount } returns param.viewerCount
                },
                annotatableTitle = AnnotatableString.empty(),
                annotatableDescription = AnnotatableString.empty(),
                zoneId = ZoneId.of("Asia/Tokyo"),
                locale = Locale.JAPAN,
            )
            // exercise
            val actual = sut.statsText
            // verify
            assertThat(actual).isEqualTo("Starting:${param.actual}")
        }

        internal data class TestParam(
            val scheduledStartDateTime: Instant,
            val viewerCount: BigInteger?,
            val actual: String,
        )
    }
}
