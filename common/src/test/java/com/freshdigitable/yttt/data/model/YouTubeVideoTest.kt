package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.test.fromRemote
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoTest {
    class UpcomingHasScheduledDateTime {
        private val scheduledStartDateTime =
            Instant.ofEpochMilli(2000) + YouTubeVideo.MAX_AGE_DEFAULT
        private val video: YouTubeVideo = YouTubeVideoImpl(
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
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_DEFAULT)
        }

        @Test
        fun upcomingIsUpdatableAtScheduledDatetimeNearScheduledDatetime() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2001)
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge)
                .isEqualTo(Duration.between(fetchedAt, video.scheduledStartDateTime))
        }

        @Test
        fun upcomingIsUpdatableAtScheduledDatetime_within30MinOfScheduledDatetime() {
            // setup
            val fetchedAt = scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge)
                .isEqualTo(Duration.between(fetchedAt, video.scheduledStartDateTime))
        }

        @Test
        fun postponedUpcomingIsUpdatableAfterDefaultDuration() {
            // setup
            val fetchedAt =
                scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON.plusMillis(1)
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_DEFAULT)
        }
    }

    class Other {
        @Test
        fun freeChatIsUpdatableAfterFreeChatDuration() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2000)
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                scheduledStartDateTime = Instant.ofEpochMilli(100000),
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = true)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_FREE_CHAT)
        }

        @Test
        fun uploadedVideoIsNotUpdatable() {
            // setup
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "uploaded video",
                liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(Instant.ofEpochMilli(2000)))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_NOT_UPDATABLE)
        }

        @Test
        fun unscheduledLiveIsUpdatableAfterDefaultDuration() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2000)
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "unscheduled live",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_DEFAULT)
        }

        @Test
        fun liveIsUpdatableAfterLiveDuration() {
            // setup
            val fetchedAt = Instant.ofEpochMilli(2000)
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "live",
                liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
                scheduledStartDateTime = Instant.ofEpochMilli(100),
                actualStartDateTime = Instant.ofEpochMilli(100),
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                .extend(old = null, isFreeChat = false)
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(YouTubeVideo.MAX_AGE_ON_AIR)
        }
    }
}
