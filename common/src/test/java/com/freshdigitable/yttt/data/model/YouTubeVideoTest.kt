package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoTest {
    class ExtendCreatorChangeFromUpcoming {
        private val old = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "video",
            scheduledStartDateTime = Instant.ofEpochSecond(1000),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        private val oldEx = object : YouTubeVideoExtended, YouTubeVideo by old {
            override val isFreeChat: Boolean = false
        }

        @Test
        fun init_returnsFalse() {
            // setup
            val current = old
            // exercise
            val actual = current.extend(null)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun notUpdated_returnsFalse() {
            // setup
            val current = old.copy()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changedTitleToFreeChat_returnsTrue() {
            // setup
            val current = old.copy(title = "free chat")
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isTrue()
        }

        @Test
        fun changedTitleToFreeChatAndArchived_returnsFalse() {
            // setup
            val current = old.copy(
                title = "free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            )
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changedTitleTo_returnsTrue() {
            // setup
            val current = old.copy(title = "changed title")
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changedToExtended_returnsTheExtended() {
            // setup
            val current = object : YouTubeVideoExtended,
                YouTubeVideo by old.copy(title = "changed title") {
                override val isFreeChat: Boolean = true
            }
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual).isSameAs(actual)
            assertThat(actual.isFreeChat).isTrue()
        }
    }

    class ExtendCreatorChangeFromFreeChat {
        private val old = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "free chat",
            scheduledStartDateTime = Instant.ofEpochSecond(1000),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        private val oldEx = object : YouTubeVideoExtended, YouTubeVideo by old {
            override val isFreeChat: Boolean = true
        }

        @Test
        fun init_returnsTrue() {
            // setup
            val current = old
            // exercise
            val actual = current.extend(null)
            // verify
            assertThat(actual.isFreeChat).isTrue()
        }

        @Test
        fun notUpdated_returnsTrue() {
            // setup
            val current = old.copy()
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isTrue()
        }

        @Test
        fun changeTitle_returnsFalse() {
            // setup
            val current = old.copy(title = "changed title")
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changeTitleToLiveStream_returnsFalse() {
            // setup
            val current = old.copy(
                title = "recycle free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            )
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changeToLiveStream_returnsFalse() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE)
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changeToArchived_returnsFalse() {
            // setup
            val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual.isFreeChat).isFalse()
        }

        @Test
        fun changeToExtended_returnsSameObject() {
            // setup
            val current = object : YouTubeVideoExtended,
                YouTubeVideo by old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE) {
                override val isFreeChat: Boolean = false
            }
            // exercise
            val actual = current.extend(oldEx)
            // verify
            assertThat(actual).isSameAs(current)
        }
    }
}

private data class YouTubeVideoImpl(
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
    override val liveBroadcastContent: YouTubeVideo.BroadcastType?
) : YouTubeVideo {
    override fun needsUpdate(current: Instant): Boolean {
        throw NotImplementedError()
    }
}
