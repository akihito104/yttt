package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.local.fixture.LiveDataSourceTestRule
import com.freshdigitable.yttt.test.testWithRefresh
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class LiveTimelineFreeChatItemDaoTest {
    companion object {
        private val channels = listOf(
            YouTubeChannelTable(YouTubeChannel.Id("channel-0")),
            YouTubeChannelTable(YouTubeChannel.Id("channel-1")),
        )
        val videos = listOf(
            YouTubeVideoEntity.liveStreaming(channel = channels[0]),
            YouTubeVideoEntity.upcomingStream(channel = channels[0]),
            YouTubeVideoEntity.archivedStream(channel = channels[0]),
            YouTubeVideoEntity.unscheduledUpcoming(channel = channels[1]),
            YouTubeVideoEntity.archivedStream(channel = channels[1]),
        )
    }

    @get:Rule
    internal val dbRule = LiveDataSourceTestRule()

    @Test
    fun init() = dbRule.runWithScope {
        // exercise
        dao.getAllFreeChatPagingSource().testWithRefresh {
            // verify
            data.shouldBeEmpty()
        }
    }

    @Test
    fun anyFreeChatItemsAreNotIncluded() = dbRule.runWithScope {
        // setup
        youtube.dao.addChannelEntities(channels)
        youtube.dao.addVideoEntities(videos)
        // exercise
        dao.getAllFreeChatPagingSource().testWithRefresh {
            // verify
            data.shouldBeEmpty()
        }
    }

    @Test
    fun add5FreeChatItems_watchAllFreeChat_found5ItemsInOrder() = dbRule.runWithScope {
        // setup
        val pinned = YouTubeVideoEntity.freeChat(id = "freechat-0-1", channel = channels[0])
        val freeChats = listOf(
            YouTubeVideoEntity.freeChat(id = "freechat-0-0", channel = channels[0]),
            pinned,
            YouTubeVideoEntity.freeChat(
                id = "freechat-1-0",
                channel = channels[1],
                scheduledStartDateTime = Instant.ofEpochMilli(1000),
            ),
            YouTubeVideoEntity.freeChat(
                id = "freechat-1-1",
                title = "title1",
                channel = channels[1],
                scheduledStartDateTime = Instant.ofEpochMilli(2000),
            ),
            YouTubeVideoEntity.freeChat(
                id = "freechat-1-2",
                title = "title0",
                channel = channels[1],
                scheduledStartDateTime = Instant.ofEpochMilli(2000),
            ),
        )
        youtube.dao.addChannelEntities(channels)
        youtube.dao.addVideoEntities(freeChats + videos)
        youtube.dao.addPinnedVideo(pinned.item.id)
        // exercise
        dao.getAllFreeChatPagingSource().testWithRefresh {
            // verify
            data.asClue { actual ->
                actual.map { it.id.value }.shouldContainExactly(
                    "freechat-0-1",
                    "freechat-0-0",
                    "freechat-1-0",
                    "freechat-1-2",
                    "freechat-1-1",
                )
            }
        }
    }
}
