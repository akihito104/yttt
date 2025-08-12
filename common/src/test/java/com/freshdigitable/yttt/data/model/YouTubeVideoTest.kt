package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class YouTubeVideoTest : ShouldSpec({
    context("upcoming stream maxAge") {
        val base = Instant.ofEpochMilli(2000)
        val scheduledStartDateTime = base + YouTubeVideo.MAX_AGE_DEFAULT
        val video: YouTubeVideo = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "upcoming live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = scheduledStartDateTime,
        )

        listOf(
            base to YouTubeVideo.MAX_AGE_DEFAULT,
            base + Duration.ofMillis(1) to YouTubeVideo.MAX_AGE_DEFAULT.minusMillis(1),
            scheduledStartDateTime to Duration.ofMillis(0),
            scheduledStartDateTime + Duration.ofMillis(1) to Duration.ofMillis(-1),
            scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON to YouTubeVideo.MAX_AGE_LIMIT_SOON.negated(),
            scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON.plusMillis(1) to YouTubeVideo.MAX_AGE_DEFAULT,
        ).forEach { (fetchedAt, expected) ->
            should("updatable after $expected") {
                // exercise
                val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                    .extend(old = null, isFreeChat = false)
                // verify
                sut.cacheControl.maxAge shouldBe expected
            }
        }
    }

    should("freeChat is updatable after freeChat duration") {
        // setup
        val video: YouTubeVideo = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "free chat",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            scheduledStartDateTime = Instant.ofEpochMilli(100000),
        )
        // exercise
        val sut = video.toUpdatable(CacheControl.fromRemote(Instant.ofEpochMilli(2000)))
            .extend(old = null, isFreeChat = true)
        // verify
        sut.cacheControl.maxAge shouldBe YouTubeVideo.MAX_AGE_FREE_CHAT
    }

    should("uploaded video is not updatable") {
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
        sut.cacheControl.maxAge shouldBe YouTubeVideo.MAX_AGE_NOT_UPDATABLE
    }

    should("unscheduled live is updatable after default duration") {
        // setup
        val video: YouTubeVideo = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "unscheduled live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )
        // exercise
        val sut = video.toUpdatable(CacheControl.fromRemote(Instant.ofEpochMilli(2000)))
            .extend(old = null, isFreeChat = false)
        // verify
        sut.cacheControl.maxAge shouldBe YouTubeVideo.MAX_AGE_DEFAULT
    }

    should("live is updatable after live duration") {
        // setup
        val video: YouTubeVideo = YouTubeVideoImpl(
            id = YouTubeVideo.Id("video"),
            title = "live",
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            scheduledStartDateTime = Instant.ofEpochMilli(100),
            actualStartDateTime = Instant.ofEpochMilli(100),
        )
        // exercise
        val sut = video.toUpdatable(CacheControl.fromRemote(Instant.ofEpochMilli(2000)))
            .extend(old = null, isFreeChat = false)
        // verify
        sut.cacheControl.maxAge shouldBe YouTubeVideo.MAX_AGE_ON_AIR
    }
})
