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

    class SimpleFind {
        @JvmField
        @Rule
        internal val dbRule = DatabaseTestRule()

        @Before
        fun setup() = dbRule.runWithDao { dao ->
            val channel = YouTubeChannelTable(id = YouTubeChannel.Id("channel"))
            val videos = listOf(
                YouTubeVideoTable(id = YouTubeVideo.Id("test"), channelId = channel.id),
                YouTubeVideoTable(id = YouTubeVideo.Id("test_no_expire"), channelId = channel.id),
            )
            val expire = YouTubeVideoExpireTable(
                videoId = videos[0].id,
                expiredAt = Instant.ofEpochMilli(100),
            )
            dao.addChannels(listOf(channel))
            dao.addVideoEntities(videos)
            dao.addLiveVideoExpire(listOf(expire))
        }

        @Test
        fun findVideosById_notYetExpired_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = YouTubeVideo.Id("test")
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(99))
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
        }

        @Test
        fun findVideosById_hasExpired_itemNotFound() = dbRule.runWithDao { dao ->
            // setup
            val target = YouTubeVideo.Id("test")
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(100))
            // verify
            assertThat(actual).hasSize(0)
        }

        @Test
        fun findVideosById_hasNoExpireEntity_found1Item() = dbRule.runWithDao { dao ->
            // setup
            val target = YouTubeVideo.Id("test_no_expire")
            // exercise
            val actual = dao.findVideosById(listOf(target), Instant.ofEpochMilli(100))
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().id).isEqualTo(target)
        }

        @Test
        fun findVideosById_found2Items() = dbRule.runWithDao { dao ->
            // setup
            val target = listOf(YouTubeVideo.Id("test"), YouTubeVideo.Id("test_no_expire"))
            // exercise
            val actual = dao.findVideosById(target, Instant.ofEpochMilli(99))
            // verify
            assertThat(actual.map { it.id }).containsExactlyInAnyOrderElementsOf(target)
        }
    }
}
