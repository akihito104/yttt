package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class YouTubeVideoTest : ShouldSpec(
    {
        context("upcoming stream maxAge") {
            val base = Instant.fromEpochMilliseconds(2000)
            val scheduledStartDateTime = base + YouTubeVideo.MAX_AGE_DEFAULT
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "upcoming live",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                scheduledStartDateTime = scheduledStartDateTime,
            )

            withData(
                nameFn = { (_, expected) -> "updatable after $expected" },
                base to YouTubeVideo.MAX_AGE_DEFAULT,
                base + 1.milliseconds to YouTubeVideo.MAX_AGE_DEFAULT - 1.milliseconds,
                scheduledStartDateTime to Duration.ZERO,
                scheduledStartDateTime + 1.milliseconds to -(1.milliseconds),
                scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON to -YouTubeVideo.MAX_AGE_LIMIT_SOON,
                scheduledStartDateTime + YouTubeVideo.MAX_AGE_LIMIT_SOON + 1.milliseconds to YouTubeVideo.MAX_AGE_DEFAULT,
            ) { (fetchedAt, expected) ->
                // exercise
                val sut = video.toUpdatable(CacheControl.fromRemote(fetchedAt))
                    .extend(old = null, isFreeChat = false)
                // verify
                sut.cacheControl.maxAge shouldBe expected
            }
        }

        should("freeChat is updatable after freeChat duration") {
            // setup
            val video: YouTubeVideo = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "free chat",
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                scheduledStartDateTime = Instant.fromEpochMilliseconds(100000),
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(Instant.fromEpochMilliseconds(2000)))
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
            val sut = video.toUpdatable(CacheControl.fromRemote(Instant.fromEpochMilliseconds(2000)))
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
            val sut = video.toUpdatable(CacheControl.fromRemote(Instant.fromEpochMilliseconds(2000)))
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
                scheduledStartDateTime = Instant.fromEpochMilliseconds(100),
                actualStartDateTime = Instant.fromEpochMilliseconds(100),
            )
            // exercise
            val sut = video.toUpdatable(CacheControl.fromRemote(Instant.fromEpochMilliseconds(2000)))
                .extend(old = null, isFreeChat = false)
            // verify
            sut.cacheControl.maxAge shouldBe YouTubeVideo.MAX_AGE_ON_AIR
        }
    },
)
