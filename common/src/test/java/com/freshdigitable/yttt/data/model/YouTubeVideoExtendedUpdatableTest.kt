package com.freshdigitable.yttt.data.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class YouTubeVideoExtendedUpdatableTest {
    @Test
    fun freeChatIsUpdatableAfterFreeChatDuration() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "free chat",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = Instant.ofEpochMilli(100000),
        ).extended(true)
        val fetchedAt = Instant.ofEpochMilli(2000)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_FREE_CHAT)
    }

    @Test
    fun uploadedVideoIsNotUpdatable() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "uploaded video",
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        ).extended(false)
        val fetchedAt = Instant.ofEpochMilli(2000)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(YouTubeVideoExtendedUpdatable.NOT_UPDATABLE)
    }

    @Test
    fun unscheduledLiveIsUpdatableAfterDefaultDuration() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "unscheduled live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).extended(false)
        val fetchedAt = Instant.ofEpochMilli(2000)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_DEFAULT)
    }

    @Test
    fun liveIsUpdatableAfterLiveDuration() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            scheduledStartDateTime = Instant.ofEpochMilli(100),
            actualStartDateTime = Instant.ofEpochMilli(100),
        ).extended(false)
        val fetchedAt = Instant.ofEpochMilli(2000)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_ON_AIR)
    }

    @Test
    fun upcomingIsUpdatableAfterDefaultDurationBeforeScheduledDatetime() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "upcoming live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = Instant.ofEpochMilli(2000) + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_DEFAULT,
        ).extended(false)
        val fetchedAt = Instant.ofEpochMilli(2000)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_DEFAULT)
    }

    @Test
    fun upcomingIsUpdatableAtScheduledDatetimeNearScheduledDatetime() {
        // setup
        val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "upcoming live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = Instant.ofEpochMilli(2000) + YouTubeVideoExtendedUpdatable.UPDATABLE_DURATION_DEFAULT,
        ).extended(false)
        val fetchedAt = Instant.ofEpochMilli(2001)
        // exercise
        val sut = video.updatable(fetchedAt)
        // verify
        assertThat(sut.updatableAt).isEqualTo(video.scheduledStartDateTime)
    }
}
