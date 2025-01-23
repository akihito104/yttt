package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.feature.timetable.UpcomingLiveVideo.Companion.asUpcoming
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

class TimetablePageDelegateKtTest {
    private val jstZone = ZoneId.of("Asia/Tokyo")

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsPreviousDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
        ).asUpcoming(
            Duration.ofHours(2),
            LocalDateTime.ofInstant(Instant.parse("2025-01-20T00:00:00.000+09:00"), jstZone)
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
        ).asUpcoming(
            Duration.ofHours(2),
            LocalDateTime.ofInstant(
                Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
                jstZone,
            ),
        )
        // exercise
        val actual = video.isStreamTodayOnwards
        // verify
        assertThat(actual).isTrue()
    }

    @Test
    fun isStreamTodayOnwards_offset2Hours_returnsFalse() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00").minusMillis(1),
        ).asUpcoming(
            Duration.ofHours(2),
            LocalDateTime.ofInstant(Instant.parse("2025-01-23T02:00:00.000+09:00"), jstZone),
        )
        // exercise
        val actual = video.isStreamTodayOnwards
        // verify
        assertThat(actual).isFalse()
    }

    @Test
    fun scheduledStartLocalDateWithOffset_offset2Hours_returnsSameDate() {
        // setup
        val video = entity(
            scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00"),
        ).asUpcoming(
            Duration.ofHours(2),
            LocalDateTime.ofInstant(Instant.parse("2025-01-20T00:00:00.000+09:00"), jstZone),
        )
        // exercise
        val actual = video.scheduledStartLocalDateWithOffset(jstZone)
        // verify
        assertThat(actual).isEqualTo(LocalDate.of(2025, Month.JANUARY, 23))
    }
}

private fun entity(
    scheduledStartDateTime: Instant,
): LiveVideo = LiveVideoEntity(
    id = LiveVideo.Id("video", YouTubeVideo.Id::class),
    title = "title",
    scheduledStartDateTime = scheduledStartDateTime,
    channel = LiveChannelEntity(
        id = LiveChannel.Id("channel", YouTubeChannel.Id::class),
        title = "channel",
        iconUrl = "",
        platform = YouTube,
    ),
    thumbnailUrl = "",
    url = "",
)
