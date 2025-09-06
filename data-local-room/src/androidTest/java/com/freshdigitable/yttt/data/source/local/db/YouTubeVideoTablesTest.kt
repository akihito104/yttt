package com.freshdigitable.yttt.data.source.local.db

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.Updatable.Companion.isFresh
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule
import io.kotest.assertions.asClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoTablesTest {
    class Init {
        @get:Rule
        internal val dbRule = YouTubeDatabaseTestRule()

        @Test
        fun findApis_returnEmpty() = dbRule.runWithDao { dao ->
            dao.findAllArchivedVideos().shouldBeEmpty()
            dao.findUnusedVideoIds().shouldBeEmpty()
            dao.findVideosById(listOf(YouTubeVideo.Id("test"))).shouldBeEmpty()
            dao.findFreeChatItems(listOf(YouTubeVideo.Id("test"))).shouldBeEmpty()
        }

        @Test
        fun watchAllUnfinished_returnEmpty() = dbRule.runWithDao { dao ->
            // exercise
            val actual = dao.watchAllUnfinishedVideos()
            // verify
            actual.test {
                awaitItem().shouldBeEmpty()
            }
        }
    }

    class ItemsAddedByDao {
        @get:Rule
        internal val dbRule = YouTubeDatabaseTestRule()
        private val simple = YouTubeVideo.Id("test")
        private val freechat = YouTubeVideo.Id("test_freechat")
        private val hasNoExpire = YouTubeVideo.Id("test_no_expire")

        @Before
        fun setup() = dbRule.runWithDao { dao ->
            val channel = YouTubeChannelTable(id = YouTubeChannel.Id("channel"))
            val videos = listOf(
                YouTubeVideoEntity.upcomingStream(id = simple.value, channel = channel),
                YouTubeVideoEntity.freeChat(id = freechat.value, channel = channel),
            )
            val expire = listOf(simple, freechat).map {
                YouTubeVideoExpireTable(
                    videoId = it,
                    cacheControl = CacheControlDb(Instant.ofEpochMilli(100), Duration.ZERO),
                )
            }
            dao.addChannelEntities(listOf(channel))
            dao.addVideoEntities(videos)
            dao.addVideos(
                listOf(YouTubeVideoTable(hasNoExpire, YouTubeVideo.BroadcastType.UPCOMING))
            )
            dao.addVideoDetails(
                listOf(YouTubeVideoDetailTable(hasNoExpire, channelId = channel.id))
            )
            dao.addLiveVideoExpire(expire)
            dao.addFreeChatItems(listOf(FreeChatTable(freechat, true)))
        }

        @Test
        fun findVideosById_notYetExpired_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(99)) }
            // verify
            actual.size shouldBe 1
            actual.first().item.id shouldBe target
        }

        @Test
        fun findVideosById_hasExpired_itemNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(100)) }
            // verify
            actual.size shouldBe 0
        }

        @Test
        fun findVideosById_freechat_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = freechat
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(99)) }
            // verify
            actual.size shouldBe 1
            actual.first().item.asClue {
                it.id shouldBe target
                it.isFreeChat shouldBe true
            }
        }

        @Test
        fun findVideosById_hasNoExpireEntity_isNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = hasNoExpire
            // exercise
            val actual = dao.findVideosById(listOf(target))
            // verify
            actual.shouldHaveSize(1)
            actual.first().asClue {
                it.item.id shouldBe target
                it.cacheControl.asClue { c ->
                    c.fetchedAt.shouldBeNull()
                    c.maxAge.shouldBeNull()
                }
            }
        }

        @Test
        fun watchAllUnfinishedVideos() = dbRule.runWithDao { dao ->
            // setup
            val channelId = YouTubeChannel.Id("channel_")
            val channel = YouTubeChannelTable(channelId)
            dao.addChannels(listOf(channel))
            val live = YouTubeVideo.Id("test_live")
            val upcoming = YouTubeVideo.Id("test_upcoming")
            dao.addVideoEntities(
                listOf(
                    YouTubeVideoEntity.liveStreaming(
                        id = live.value,
                        channel = channel,
                        scheduledStartDateTime = Instant.ofEpochMilli(10),
                        actualStartDateTime = Instant.ofEpochMilli(14),
                    ),
                    YouTubeVideoEntity.upcomingStream(
                        id = upcoming.value,
                        channel = channel,
                        scheduledStartDateTime = Instant.ofEpochMilli(30),
                    ),
                    YouTubeVideoEntity.archivedStream(
                        id = "test_archived",
                        channel = channel,
                        scheduledStartDateTime = Instant.ofEpochMilli(50),
                        actualStartDateTime = Instant.ofEpochMilli(51),
                        actualEndDateTime = Instant.ofEpochMilli(70),
                    ),
                    YouTubeVideoEntity.uploadedVideo(
                        id = "test_shorts",
                        channel = channel,
                    ),
                )
            )
            // exercise
            val actual = dao.watchAllUnfinishedVideos()
            // verify
            actual.test {
                val item = awaitItem()
                item.asClue {
                    it.forAll { i -> i.liveBroadcastContent != YouTubeVideo.BroadcastType.NONE }
                    it.map { i -> i.id }.shouldContainExactlyInAnyOrder(
                        simple, freechat, hasNoExpire, live, upcoming,
                    )
                }
            }
        }
    }
}
