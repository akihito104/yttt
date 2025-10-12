package com.freshdigitable.yttt.data.source.local.db

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class LiveFreeChatDaoTest {
    @get:Rule
    internal val dbRule = YouTubeDatabaseTestRule()

    @Test
    fun init() = dbRule.runWithDao {
        // setup
        val sut = dbRule.database.liveTimelineFreeChatItemDao
        // exercise
        sut.watchAllFreeChat().test {
            awaitItem().shouldBeEmpty()
        }
    }

    @Test
    fun add5FreeChatItems_watchAllFreeChat_found5ItemsInOrder() = dbRule.runWithLocalSource {
        // setup
        val channels = listOf(
            YouTubeChannelTable(YouTubeChannel.Id("channel-0")),
            YouTubeChannelTable(YouTubeChannel.Id("channel-1")),
        )
        val pinned = YouTubeVideoEntity.freeChat(id = "freechat-0-1", channel = channels[0])
        val videos = listOf(
            YouTubeVideoEntity.freeChat(id = "freechat-0-0", channel = channels[0]),
            pinned,
            YouTubeVideoEntity.liveStreaming(channel = channels[0]),
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
        dao.addChannelEntities(channels)
        dao.addVideoEntities(videos)
        dao.addPinnedVideo(pinned.item.id)
        val sut = dbRule.database.liveTimelineFreeChatItemDao
        // exercise
        sut.watchAllFreeChat().test {
            awaitItem().asClue { actual ->
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
