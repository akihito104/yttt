package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveVideo.Upcoming.Companion.scheduledStartLocalDateWithOffset
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.feature.timetable.YouTubeUpcomingLiveVideo.Companion.isStreamTodayOnwards
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

class YouTubeUpcomingLiveVideoTest {
    private val jstZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsPreviousDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            changeDateTime = 26,
        )
        // exercise
        val actual = video.scheduledStartLocalDateWithOffset(jstZone)
        // verify
        assertThat(actual).isEqualTo(LocalDate.of(2025, Month.JANUARY, 22))
    }

    @Test
    fun isStreamTodayOnwards_offset2Hours_returnsTrue() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            changeDateTime = 26,
        )
        val current = LocalDateTime.ofInstant(
            Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            jstZone,
        )
        // exercise
        val actual = video.isStreamTodayOnwards(current)
        // verify
        assertThat(actual).isTrue()
    }

    @Test
    fun isStreamTodayOnwards_offset2Hours_returnsFalse() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
            changeDateTime = 26,
        )
        val current =
            LocalDateTime.ofInstant(Instant.parse("2025-01-23T02:00:00.000+09:00"), jstZone)
        // exercise
        val actual = video.isStreamTodayOnwards(current)
        // verify
        assertThat(actual).isFalse()
    }

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsSameDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00"),
            changeDateTime = 26,
        )
        // exercise
        val actual = video.scheduledStartLocalDateWithOffset(jstZone)
        // verify
        assertThat(actual).isEqualTo(LocalDate.of(2025, Month.JANUARY, 23))
    }
}

private fun entity(scheduledStartDateTime: Instant, changeDateTime: Int): YouTubeUpcomingLiveVideo {
    return YouTubeUpcomingLiveVideo(
        changeDateTime = changeDateTime,
        video = object : YouTubeVideoExtended {
            override val scheduledStartDateTime: Instant
                get() = scheduledStartDateTime
            override val isFreeChat: Boolean get() = TODO("Not yet implemented")
            override val id: YouTubeVideo.Id get() = TODO("Not yet implemented")
            override val title: String get() = TODO("Not yet implemented")
            override val channel: YouTubeChannel get() = TODO("Not yet implemented")
            override val thumbnailUrl: String get() = TODO("Not yet implemented")
            override val scheduledEndDateTime: Instant get() = TODO("Not yet implemented")
            override val actualStartDateTime: Instant get() = TODO("Not yet implemented")
            override val actualEndDateTime: Instant get() = TODO("Not yet implemented")
            override val description: String get() = TODO("Not yet implemented")
            override val viewerCount: BigInteger get() = TODO("Not yet implemented")
            override val liveBroadcastContent: YouTubeVideo.BroadcastType get() = TODO("Not yet implemented")
            override val updatableAt: Instant get() = TODO("Not yet implemented")
        },
    )
}
