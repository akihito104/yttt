package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.Updatable.Companion.isFresh
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.local.fixture.LiveDataSourceTestRule
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule
import com.freshdigitable.yttt.data.source.local.fixture.findAllArchivedVideos
import com.freshdigitable.yttt.test.testWithRefresh
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
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
        fun findApis_returnEmpty() = dbRule.runWithScope {
            dao.findUnusedVideoIds().shouldBeEmpty()
            dao.findVideosById(listOf(YouTubeVideo.Id("test"))).shouldBeEmpty()
            dao.findFreeChatItems(listOf(YouTubeVideo.Id("test"))).shouldBeEmpty()
        }
    }

    class ItemsAddedByDao {
        @get:Rule
        internal val dbRule = YouTubeDatabaseTestRule()
        private val simple = YouTubeVideo.Id("test")
        private val freechat = YouTubeVideo.Id("test_freechat")
        private val hasNoExpire = YouTubeVideo.Id("test_no_expire")
        private val hasNoType = YouTubeVideo.Id("test_no_type")

        @Before
        fun setup() = dbRule.runWithScope {
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
                listOf(YouTubeVideoTable(hasNoExpire, YouTubeVideo.BroadcastType.UPCOMING)),
            )
            dao.addVideoDetails(
                listOf(YouTubeVideoDetailTable(hasNoExpire, channelId = channel.id)),
            )
            dao.addLiveVideoExpire(expire)
            dao.addFreeChatItems(listOf(FreeChatTable(freechat, true)))
            dao.insertOrIgnoreVideoEntities(listOf(hasNoType))
        }

        @Test
        fun findVideosById_notYetExpired_found1Item() = dbRule.runWithScope {
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
        fun findVideosById_hasExpired_itemNotFound() = dbRule.runWithScope {
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(100)) }
            // verify
            actual.size shouldBe 0
        }

        @Test
        fun findVideosById_freechat_found1Item() = dbRule.runWithScope {
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
        fun findVideosById_hasNoExpireEntity_found1Item() = dbRule.runWithScope {
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
        fun findVideosById_hasNoTypeEntity_isNotFound() = dbRule.runWithScope {
            // setup
            val target = hasNoType
            // exercise
            val actual = dao.findVideosById(listOf(target))
            // verify
            actual.shouldBeEmpty()
        }

        @Test
        fun fetchUpdatableVideoIds_found2Items() = dbRule.runWithScope {
            // exercise
            val actual = dao.fetchUpdatableVideoIds(Instant.EPOCH)
            // verify
            actual.shouldContainAll(hasNoType, hasNoExpire)
        }

        @Test
        fun removeVideoEntities() = dbRule.runWithScope {
            // setup
            val videoIds = listOf(simple, freechat, hasNoExpire, hasNoType)
            // exercise
            dao.removeVideoEntities(videoIds)
            // verify
            database.findAllArchivedVideos().shouldBeEmpty()
            dao.findVideosById(videoIds).shouldBeEmpty()
        }

        @Test
        fun updateAsArchivedVideoEntities() = dbRule.runWithScope {
            // setup
            val videoIds = listOf(simple, freechat, hasNoExpire, hasNoType)
            // exercise
            dao.updateAsArchivedVideoEntities(videoIds)
            // verify
            database.findAllArchivedVideos().shouldContainExactlyInAnyOrder(videoIds)
            dao.findVideosById(videoIds).shouldBeEmpty()
            dao.fetchUpdatableVideoIds(Instant.EPOCH).shouldBeEmpty()
        }

        @Test
        fun watchAllUnfinishedVideos() = dbRule.runWithScope {
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
                ),
            )
            // verify
            val liveSource = LiveDataSourceTestRule.LiveDataSourceScope(ioScope, database, this)
            liveSource.pagingSource.onAir.testWithRefresh {
                data.map { it.id.value }.shouldContainExactlyInAnyOrder(live.value)
            }
            liveSource.pagingSource.upcoming(Instant.EPOCH).testWithRefresh {
                data.map { it.id.value }.shouldContainExactlyInAnyOrder(upcoming.value, simple.value)
            }
            liveSource.pagingSource.freeChat.testWithRefresh {
                data.map { it.id.value }.shouldContainExactlyInAnyOrder(freechat.value)
            }
        }
    }
}
