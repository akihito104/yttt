package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveVideo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger
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

private fun onAir(actualStartDateTime: Instant): LiveVideo.OnAir = object : LiveVideo.OnAir {
    override val actualStartDateTime: Instant
        get() = actualStartDateTime
    override val channel: LiveChannel get() = throw UnsupportedOperationException()
    override val scheduledStartDateTime: Instant get() = throw UnsupportedOperationException()
    override val scheduledEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val url: String get() = throw UnsupportedOperationException()
    override val description: String get() = throw UnsupportedOperationException()
    override val viewerCount: BigInteger get() = throw UnsupportedOperationException()
    override val id: LiveVideo.Id get() = throw UnsupportedOperationException()
    override val title: String get() = throw UnsupportedOperationException()
    override val thumbnailUrl: String get() = throw UnsupportedOperationException()
    override fun equals(other: Any?): Boolean = throw UnsupportedOperationException()
    override fun hashCode(): Int = throw UnsupportedOperationException()
}
