package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.isUpcomingWithinPublicationDeadline
import com.freshdigitable.yttt.test.FakeYouTubeClient.Companion.updatable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoExtendedTest {
    class ChangeFromUpcomingForIsFreeChat {
        private val old = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "video",
            scheduledStartDateTime = Instant.ofEpochSecond(1000),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        private val oldEx = old.extended(false).updatable()

        @Test
        fun init_freeChatIsFalse() {
            // setup
            val current = old.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(null)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun notUpdated_returnsFalse() {
            // setup
            val current = old.copy().updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changedTitleToFreeChat_returnsTrue() {
            // setup
            val current = old.copy(title = "free chat").updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isTrue()
        }

        @Test
        fun changedTitleToFreeChatAndArchived_returnsFalse() {
            // setup
            val current = old.copy(
                title = "free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            ).updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changedTitle_freeChatIsFalse() {
            // setup
            val current = old.copy(title = "changed title").updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun extendedObjectIsNotExtendedAnyMore() {
            // setup
            val current = object : YouTubeVideoExtended,
                YouTubeVideo by old.copy(title = "changed title") {
                override val channel: YouTubeChannel get() = old.channel
                override val isFreeChat: Boolean = true
            }.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual).isSameAs(actual)
            assertThat(actual.item.isFreeChat).isTrue()
        }
    }

    class ChangeFromUpcomingForIsThumbnailUpdatable {
        private val old = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "video",
            scheduledStartDateTime = Instant.ofEpochSecond(1000),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        private val oldEx = old.extended(false).updatable()

        @Test
        fun init_thumbnailIsNotUpdatable() {
            // setup
            val current = old.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(null)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isFalse()
        }

        @Test
        fun notUpdated_returnsFalse() {
            // setup
            val current = old.copy().updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isFalse()
        }

        @Test
        fun notUpdatedButFreeChat_returnsTrue() {
            // setup
            val current = old.copy().updatable<YouTubeVideo>(
                fetchedAt = Instant.EPOCH + YouTubeVideo.MAX_AGE_FREE_CHAT,
                maxAge = Duration.ZERO,
            )
            val oldAsFreeChat = old.updatable<YouTubeVideo>().extend(old = null, isFreeChat = true)
            // exercise
            val actual = current.extend(oldAsFreeChat, isFreeChat = true)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isTrue()
        }

        @Test
        fun liveIsArchived_returnsFalse() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
                .updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isFalse()
        }

        @Test
        fun titleIsChangedAndArchived_returnsFalse() {
            // setup
            val current = old.copy(
                liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
                title = "changed title",
            ).updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isFalse()
        }

        @Test
        fun liveIsStarted_returnsTrue() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE)
                .updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isTrue()
        }

        @Test
        fun titleIsChanged_returnsTrue() {
            // setup
            val current = old.copy(title = "changed title").updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isThumbnailUpdatable).isTrue()
        }

        @Test
        fun objectIsNotCreatedByExtendCreator_returnsFalse() {
            // setup
            val current = object : YouTubeVideoExtended,
                YouTubeVideo by old.copy(title = "changed title") {
                override val channel: YouTubeChannel get() = old.channel
                override val isFreeChat: Boolean = true
            }.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual).isSameAs(actual)
            assertThat(actual.item.isThumbnailUpdatable).isFalse()
        }
    }

    class ChangeFromFreeChat {
        private val old = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "free chat",
            scheduledStartDateTime = Instant.ofEpochSecond(1000),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        private val oldEx = old.extended(true).updatable()

        @Test
        fun init_returnsTrue() {
            // setup
            val current = old.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(null)
            // verify
            assertThat(actual.item.isFreeChat).isTrue()
        }

        @Test
        fun notUpdated_returnsTrue() {
            // setup
            val current = old.copy().updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isTrue()
        }

        @Test
        fun changeTitle_returnsFalse() {
            // setup
            val current = old.copy(title = "changed title").updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changeTitleToLiveStream_returnsFalse() {
            // setup
            val current = old.copy(
                title = "recycle free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            ).updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changeToLiveStream_returnsFalse() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE)
                .updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changeToArchived_returnsFalse() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
                .updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.item.isFreeChat).isFalse()
        }

        @Test
        fun changeToExtended_returnsSameObject() {
            // setup
            val current = object : YouTubeVideoExtended,
                YouTubeVideo by old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE) {
                override val channel: YouTubeChannel get() = old.channel
                override val isFreeChat: Boolean = false
            }.updatable<YouTubeVideo>()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual).isSameAs(current)
        }
    }

    class IsUpcomingWithinPublicationDeadline {
        private val scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00")
        private val video = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "title",
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).extended(false)

        @Test
        fun returnsTrue() {
            // setup
            val current = scheduledStartDateTime + YouTubeVideo.UPCOMING_DEADLINE
            // exercise
            val actual = video.isUpcomingWithinPublicationDeadline(current)
            // verify
            assertThat(actual).isTrue()
        }

        @Test
        fun returnsFalse() {
            // setup
            val current = scheduledStartDateTime + YouTubeVideo.UPCOMING_DEADLINE.plusMillis(1)
            // exercise
            val actual = video.isUpcomingWithinPublicationDeadline(current)
            // verify
            assertThat(actual).isFalse()
        }

        @Test
        fun scheduledStartDateTimeIsNull_throwsIllegalStateException() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "title",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            ).extended(false)
            val current = scheduledStartDateTime + YouTubeVideo.UPCOMING_DEADLINE
            // exercise
            val actual = catchThrowable {
                video.isUpcomingWithinPublicationDeadline(current)
            }
            // verify
            assertThat(actual).isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun live_throwsIllegalStateException() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "title",
                scheduledStartDateTime = scheduledStartDateTime,
                liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            ).extended(false)
            val current = scheduledStartDateTime + YouTubeVideo.UPCOMING_DEADLINE
            // exercise
            val actual = catchThrowable {
                video.isUpcomingWithinPublicationDeadline(current)
            }
            // verify
            assertThat(actual).isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun freeChat_throwsIllegalStateException() {
            // setup
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "title",
                scheduledStartDateTime = scheduledStartDateTime,
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            ).extended(true)
            val current = scheduledStartDateTime + YouTubeVideo.UPCOMING_DEADLINE
            // exercise
            val actual = catchThrowable {
                video.isUpcomingWithinPublicationDeadline(current)
            }
            // verify
            assertThat(actual).isInstanceOf(IllegalStateException::class.java)
        }
    }
}

internal data class YouTubeVideoImpl(
    override val id: YouTubeVideo.Id,
    override val title: String,
    override val channel: YouTubeChannel = YouTubeChannelEntity(
        id = YouTubeChannel.Id("channel"),
        title = "channel",
        iconUrl = "",
    ),
    override val thumbnailUrl: String = "https://example.com/user/live/thumnail.png",
    override val scheduledStartDateTime: Instant? = null,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val description: String = "",
    override val viewerCount: BigInteger? = null,
    override val liveBroadcastContent: YouTubeVideo.BroadcastType?,
) : YouTubeVideo

internal fun YouTubeVideoImpl.extended(isFreeChat: Boolean): YouTubeVideoExtended =
    object : YouTubeVideoExtended, YouTubeVideo by this {
        override val channel: YouTubeChannel get() = this@extended.channel
        override val isFreeChat: Boolean = isFreeChat
    }
