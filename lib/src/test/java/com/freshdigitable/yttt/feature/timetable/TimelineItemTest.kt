package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveVideo
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TimelineItemTest {
    @Test
    fun localDateTimeText_JP_returnsCurrentDate() {
        // setup
        val sut = TimelineItem(
            video = onAir(Instant.parse("2025-01-23T02:00:00.000+09:00")),
            extraHourOfDay = Duration.ofHours(2),
            zoneId = ZoneId.of("Asia/Tokyo"),
            locale = Locale.JAPAN,
        )
        // exercise
        val actual = sut.localDateTimeText
        // verify
        assertThat(actual).isEqualTo("2025/01/23(木) 02:00")
    }

    @Test
    fun localDateTimeText_JP_returnsYesterdayDate() {
        // setup
        val sut = TimelineItem(
            video = onAir(Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1)),
            extraHourOfDay = Duration.ofHours(2),
            zoneId = ZoneId.of("Asia/Tokyo"),
            locale = Locale.JAPAN,
        )
        // exercise
        val actual = sut.localDateTimeText
        // verify
        assertThat(actual).isEqualTo("2025/01/22(水) 25:59")
    }

    @Test
    fun localDateTimeText_US() {
        // setup
        val sut = TimelineItem(
            video = onAir(Instant.parse("2025-01-23T00:00:00.000-05:00")),
            extraHourOfDay = Duration.ofHours(2),
            zoneId = ZoneId.of("America/New_York"),
            locale = Locale.US,
        )
        // exercise
        val actual = sut.localDateTimeText
        // verify
        assertThat(actual).isEqualTo("2025/01/22(Wed) 24:00")
    }
}

private fun onAir(actualStartDateTime: Instant): LiveVideo.OnAir = mockk<LiveVideo.OnAir>().apply {
    every { this@apply.actualStartDateTime } returns actualStartDateTime
}
