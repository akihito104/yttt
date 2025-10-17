package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.source.local.fixture.LiveDataSourceTestRule
import com.freshdigitable.yttt.test.testWithRefresh
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.Rule
import org.junit.Test

class LiveLocalPagingSourceTest {
    @get:Rule
    internal val rule = LiveDataSourceTestRule()

    @Test
    fun watchAllUnfinished_returnEmpty() = rule.runWithScope {
        // verify
        pagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
        pagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
        pagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
    }

    @Test
    fun addVideo_addedLiveAndUpcomingItems() = rule.runWithScope {
        // setup
        val unfinished = listOf(
            YouTubeVideoEntity.liveStreaming(id = "live_streaming"),
            YouTubeVideoEntity.upcomingStream(id = "upcoming"),
            YouTubeVideoEntity.unscheduledUpcoming(id = "upcoming_unscheduled"),
        )
        val inactive = listOf(
            YouTubeVideoEntity.uploadedVideo(),
            YouTubeVideoEntity.archivedStream(),
        )
        val video = unfinished + inactive
        val channels = video.map { it.item.channel }.distinctBy { it.id }
        youtube.dao.addChannelEntities(channels)
        // exercise
        youtube.extendedSource.addVideo(video)
        // verify
        val found = youtube.dao.findVideosById(video.map { it.item.id })
        found.containsVideoIdInAnyOrderElementsOf(video)
        pagingSource.onAir.testWithRefresh {
            data.map { it.id.value }.shouldContainInOrder("live_streaming")
        }
        pagingSource.upcoming.testWithRefresh {
            data.map { it.id.value }.shouldContainInOrder("upcoming")
        }
        youtube.dao.findAllArchivedVideos()
            .shouldContainExactlyInAnyOrder(inactive.map { it.item.id })
        youtube.dao.findUnusedVideoIds().shouldContainExactlyInAnyOrder(inactive.map { it.item.id })
    }
}
