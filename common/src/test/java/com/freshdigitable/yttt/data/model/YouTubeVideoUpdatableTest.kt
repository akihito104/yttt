package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoUpdatableTest {
    class UpcomingHasScheduledDateTime {
        private val scheduledStartDateTime =
            Instant.ofEpochMilli(2000) + YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT
        private val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "upcoming live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = scheduledStartDateTime,
        )

        @Test
        fun upcomingIsUpdatableAfterDefaultDurationBeforeScheduledDatetime() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2000)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT)
        }

        @Test
        fun upcomingIsUpdatableAtScheduledDatetimeNearScheduledDatetime() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2001)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(video.scheduledStartDateTime)
        }

        @Test
        fun upcomingIsUpdatableAtScheduledDatetime_within30MinOfScheduledDatetime() {
            // setup
            val fetchedAt = scheduledStartDateTime + YouTubeVideoUpdatable.UPDATABLE_LIMIT_SOON
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(video.scheduledStartDateTime)
        }

        @Test
        fun postponedUpcomingIsUpdatableAfterDefaultDuration() {
            // setup
            val fetchedAt =
                scheduledStartDateTime + YouTubeVideoUpdatable.UPDATABLE_LIMIT_SOON.plusMillis(1)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT)
        }
    }

    class Other {
        @Test
        fun freeChatIsUpdatableAfterFreeChatDuration() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                scheduledStartDateTime = Instant.ofEpochMilli(100000),
            )
            val fetchedAt = Instant.ofEpochMilli(2000)
            // exercise
            val sut = video.extend(old = null, isFreeChat = true, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoUpdatable.UPDATABLE_DURATION_FREE_CHAT)
        }

        @Test
        fun uploadedVideoIsNotUpdatable() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "uploaded video",
                liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            )
            val fetchedAt = Instant.ofEpochMilli(2000)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(YouTubeVideoUpdatable.NOT_UPDATABLE)
        }

        @Test
        fun unscheduledLiveIsUpdatableAfterDefaultDuration() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "unscheduled live",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
            val fetchedAt = Instant.ofEpochMilli(2000)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoUpdatable.UPDATABLE_DURATION_DEFAULT)
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
            )
            val fetchedAt = Instant.ofEpochMilli(2000)
            // exercise
            val sut = video.extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)
            // verify
            assertThat(sut.updatableAt).isEqualTo(fetchedAt + YouTubeVideoUpdatable.UPDATABLE_DURATION_ON_AIR)
        }
    }
}
