package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeVideoTablesTest {
    class Init {
        @JvmField
        @Rule
        internal val dbRule = DatabaseTestRule()

        @Test
        fun initial() = dbRule.runWithDao { dao ->
            assertThat(dao.findAllArchivedVideos()).isEmpty()
            assertThat(dao.findUnusedVideoIds()).isEmpty()
            assertThat(dao.findVideosById(listOf(YouTubeVideo.Id("test")), Instant.EPOCH)).isEmpty()
            assertThat(dao.findFreeChatItems(listOf(YouTubeVideo.Id("test")))).isEmpty()
        }
    }

    class FindById {
        @JvmField
        @Rule
        internal val dbRule = DatabaseTestRule()
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
                YouTubeVideoExpireTable(videoId = it, expiredAt = Instant.ofEpochMilli(100))
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
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(99))
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
        }

        @Test
        fun findVideosById_hasExpired_itemNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = simple
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(100))
            // verify
            assertThat(actual).hasSize(0)
        }

        @Test
        fun findVideosById_freechat_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = freechat
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(99))
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
            assertThat(actual.first().isFreeChat).isTrue()
        }

        @Test
        fun findVideosById_hasNoExpireEntity_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = hasNoExpire
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(100))
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
        }

        @Test
        fun findVideosById_found2Items() = dbRule.runWithDao { dao ->
            // setup
            val target = listOf(simple, hasNoExpire)
            // exercise
            val actual = dao.findVideosById(target, Instant.ofEpochMilli(99))
            // verify
            assertThat(actual.map { it.id }).containsExactlyInAnyOrderElementsOf(target)
        }
    }
}
