package com.freshdigitable.yttt.data.source.local.db

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isFresh
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import org.assertj.core.api.Assertions.assertThat
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
            assertThat(dao.findAllArchivedVideos()).isEmpty()
            assertThat(dao.findUnusedVideoIds()).isEmpty()
            assertThat(dao.findVideosById(listOf(YouTubeVideo.Id("test")))).isEmpty()
            assertThat(dao.findFreeChatItems(listOf(YouTubeVideo.Id("test")))).isEmpty()
        }

        @Test
        fun watchAllUnfinished_returnEmpty() = dbRule.runWithDao { dao ->
            // exercise
            val actual = dao.watchAllUnfinishedVideos()
            // verify
            actual.test {
                assertThat(awaitItem()).isEmpty()
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
            val videos = listOf(simple, freechat, hasNoExpire).map {
                YouTubeVideoTable(id = it, channelId = channel.id)
            }
            val expire = listOf(simple, freechat).map {
                YouTubeVideoExpireTable(
                    videoId = it,
                    cacheControl = CacheControlDb(Instant.ofEpochMilli(100), Duration.ZERO),
                )
            }
            dao.addChannels(listOf(channel))
            dao.addVideoEntities(videos)
            dao.addLiveVideoExpire(expire)
            dao.addFreeChatItemEntities(listOf(FreeChatTable(freechat, true)))
        }

        @Test
        fun findVideosById_notYetExpired_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(99)) }
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
        }

        @Test
        fun findVideosById_hasExpired_itemNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(100)) }
            // verify
            assertThat(actual).hasSize(0)
        }

        @Test
        fun findVideosById_freechat_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = freechat
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(99)) }
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
            assertThat(actual.first().isFreeChat).isTrue()
        }

        @Test
        fun findVideosById_hasNoExpireEntity_isNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = hasNoExpire
            // exercise
            val actual = dao.findVideosById(listOf(target))
                .filter { it.isFresh(Instant.ofEpochMilli(100)) }
            // verify
            assertThat(actual).isEmpty()
        }

        @Test
        fun findVideosById_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = listOf(simple, hasNoExpire)
            // exercise
            val actual = dao.findVideosById(target)
                .filter { it.isFresh(Instant.ofEpochMilli(99)) }
            // verify
            assertThat(actual.map { it.id }).containsExactlyInAnyOrder(simple)
        }

        @Test
        fun watchAllUnfinishedVideos() = dbRule.runWithDao { dao ->
            // setup
            val channelId = YouTubeChannel.Id("channel_")
            dao.addChannels(listOf(YouTubeChannelTable(channelId)))
            val live = YouTubeVideo.Id("test_live")
            val upcoming = YouTubeVideo.Id("test_upcoming")
            dao.addVideoEntities(
                listOf(
                    YouTubeVideoTable(
                        id = live,
                        channelId = channelId,
                        scheduledStartDateTime = Instant.ofEpochMilli(10),
                        actualStartDateTime = Instant.ofEpochMilli(14),
                        broadcastContent = YouTubeVideo.BroadcastType.LIVE,
                    ),
                    YouTubeVideoTable(
                        id = upcoming,
                        channelId = channelId,
                        scheduledStartDateTime = Instant.ofEpochMilli(30),
                        broadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                    ),
                    YouTubeVideoTable(
                        id = YouTubeVideo.Id("test_archived"),
                        channelId = channelId,
                        scheduledStartDateTime = Instant.ofEpochMilli(50),
                        actualStartDateTime = Instant.ofEpochMilli(51),
                        actualEndDateTime = Instant.ofEpochMilli(70),
                        broadcastContent = YouTubeVideo.BroadcastType.NONE,
                    ),
                    YouTubeVideoTable(
                        id = YouTubeVideo.Id("test_shorts"),
                        channelId = channelId,
                        broadcastContent = YouTubeVideo.BroadcastType.NONE,
                    ),
                )
            )
            // exercise
            val actual = dao.watchAllUnfinishedVideos()
            // verify
            actual.test {
                val item = awaitItem()
                assertThat(item).hasSize(5)
                    .allMatch { it.liveBroadcastContent != YouTubeVideo.BroadcastType.NONE }
                assertThat(item.map { it.id }).containsExactlyInAnyOrder(
                    simple, freechat, hasNoExpire, live, upcoming,
                )
            }
        }
    }
}
