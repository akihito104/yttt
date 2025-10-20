package com.freshdigitable.yttt.data.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import java.math.BigInteger
import java.time.Instant

class LiveVideoTest : ShouldSpec(
    {
        should("comparator_onAir sorts LiveVideoOnAirEntity correctly") {
            val id1 = LiveVideo.Id("video1", YouTubeVideo.Id::class)
            val id2 = LiveVideo.Id("video2", YouTubeVideo.Id::class)
            val id3 = LiveVideo.Id("video3", TwitchVideo.Id::class)
            val id4 = LiveVideo.Id("video4", YouTubeVideo.Id::class)
            val target = listOf(
                LiveVideoOnAirEntity(
                    id = id1,
                    actualStartDateTime = Instant.EPOCH,
                    title = "title",
                ),
                LiveVideoOnAirEntity(
                    id = id2,
                    actualStartDateTime = Instant.ofEpochMilli(1),
                    title = "title2",
                ),
                LiveVideoOnAirEntity(
                    id = id3,
                    actualStartDateTime = Instant.ofEpochMilli(1),
                    title = "title3",
                ),
                LiveVideoOnAirEntity(
                    id = id4,
                    actualStartDateTime = Instant.ofEpochMilli(2),
                    title = "title4",
                ),
            )
            val actual = target.sortedWith(compareBy { it })
            actual.map { it.id }.shouldContainExactly(id4, id2, id3, id1)
        }

        should("comparator_upcoming sorts LiveVideoUpcomingEntity correctly") {
            val id1 = LiveVideo.Id("video1", YouTubeVideo.Id::class)
            val id2 = LiveVideo.Id("video2", YouTubeVideo.Id::class)
            val id3 = LiveVideo.Id("video3", TwitchVideo.Id::class)
            val id4 = LiveVideo.Id("video4", YouTubeVideo.Id::class)
            val target = listOf(
                LiveVideoUpcomingEntity(
                    id = id1,
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title1",
                ),
                LiveVideoUpcomingEntity(
                    id = id2,
                    scheduledStartDateTime = Instant.ofEpochMilli(1),
                    title = "title4",
                ),
                LiveVideoUpcomingEntity(
                    id = id3,
                    scheduledStartDateTime = Instant.ofEpochMilli(2),
                    title = "title3",
                ),
                LiveVideoUpcomingEntity(
                    id = id4,
                    scheduledStartDateTime = Instant.ofEpochMilli(1),
                    title = "title2",
                ),
            )
            val actual = target.sortedWith(compareBy { it })
            actual.map { it.id }.shouldContainExactly(id1, id4, id2, id3)
        }

        should("comparator_freeChat sorts LiveVideoFreeChatEntity correctly") {
            val id1 = LiveVideo.Id("video1", YouTubeVideo.Id::class)
            val id2 = LiveVideo.Id("video2", YouTubeVideo.Id::class)
            val id3 = LiveVideo.Id("video3", YouTubeVideo.Id::class)
            val id4 = LiveVideo.Id("video4", YouTubeVideo.Id::class)
            val id5 = LiveVideo.Id("video5", YouTubeVideo.Id::class)
            val id6 = LiveVideo.Id("video6", YouTubeVideo.Id::class)
            val target = listOf(
                LiveVideoFreeChatEntity(
                    id = id1,
                    channelId = LiveChannel.Id("channel1", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title1",
                ),
                LiveVideoFreeChatEntity(
                    id = id2,
                    channelId = LiveChannel.Id("channel2", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.ofEpochMilli(1),
                    title = "title2",
                ),
                LiveVideoFreeChatEntity(
                    id = id3,
                    channelId = LiveChannel.Id("channel3", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title3",
                ),
                LiveVideoFreeChatEntity(
                    id = id4,
                    channelId = LiveChannel.Id("channel2", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title5",
                ),
                LiveVideoFreeChatEntity(
                    id = id5,
                    channelId = LiveChannel.Id("channel2", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title4",
                ),
                LiveVideoFreeChatEntity(
                    id = id6,
                    channelId = LiveChannel.Id("channel4", YouTubeVideo.Id::class),
                    scheduledStartDateTime = Instant.EPOCH,
                    title = "title6",
                    isPinned = true,
                ),
            )
            val actual = target.sortedWith(compareBy { it })
            actual.map { it.id }.shouldContainExactly(id6, id1, id5, id4, id2, id3)
        }
    },
)

internal data class LiveVideoOnAirEntity(
    override val id: LiveVideo.Id,
    override val actualStartDateTime: Instant,
    override val title: String,
) : LiveVideo.OnAir {
    override val channel: LiveChannel get() = throw UnsupportedOperationException()
    override val scheduledStartDateTime: Instant get() = throw UnsupportedOperationException()
    override val scheduledEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val description: String get() = throw UnsupportedOperationException()
    override val viewerCount: BigInteger get() = throw UnsupportedOperationException()
    override val thumbnailUrl: String get() = throw UnsupportedOperationException()
}

internal data class LiveVideoUpcomingEntity(
    override val id: LiveVideo.Id,
    override val scheduledStartDateTime: Instant,
    override val title: String,
) : LiveVideo.Upcoming {
    override val channel: LiveChannel get() = throw UnsupportedOperationException()
    override val scheduledEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualStartDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val description: String get() = throw UnsupportedOperationException()
    override val viewerCount: BigInteger get() = throw UnsupportedOperationException()
    override val thumbnailUrl: String get() = throw UnsupportedOperationException()
}

internal data class LiveVideoFreeChatEntity(
    override val id: LiveVideo.Id,
    private val channelId: LiveChannel.Id,
    override val scheduledStartDateTime: Instant,
    override val title: String,
    override val isPinned: Boolean = false,
) : LiveVideo.FreeChat {
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = channelId,
            title = "",
            iconUrl = "",
            platform = object : LivePlatform {
                override val name: String get() = "dummy"
                override val color: Long get() = 0xFF000000
            },
        )
    override val scheduledEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualStartDateTime: Instant get() = throw UnsupportedOperationException()
    override val actualEndDateTime: Instant get() = throw UnsupportedOperationException()
    override val description: String get() = throw UnsupportedOperationException()
    override val viewerCount: BigInteger get() = throw UnsupportedOperationException()
    override val thumbnailUrl: String get() = throw UnsupportedOperationException()
}
