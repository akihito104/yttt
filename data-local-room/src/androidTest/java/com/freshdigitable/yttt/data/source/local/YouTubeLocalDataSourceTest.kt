package com.freshdigitable.yttt.data.source.local

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.db.DatabaseTestRule
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeLocalDataSourceTest {
    class Init {
        @get:Rule
        internal val rule = DatabaseTestRule()

        private val dateTimeProvider = DateTimeProviderFake()

        @Test
        fun videoFlowIsEmpty() = rule.runWithLocalSource(dateTimeProvider) { _, sut ->
            sut.videos.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun videoIsEmpty() = rule.runWithLocalSource(dateTimeProvider) { _, sut ->
            assertThat(sut.fetchVideoList(emptySet())).isEmpty()
            assertThat(sut.fetchVideoList(setOf(YouTubeVideo.Id("test")))).isEmpty()
        }

        @Test
        fun addVideo_addedLiveAndUpcomingItems() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val target = listOf(
                    YouTubeVideoEntity.liveStreaming(),
                    YouTubeVideoEntity.upcomingStream(),
                    YouTubeVideoEntity.unscheduledUpcoming(),
                )
                val video = target + listOf(
                    YouTubeVideoEntity.uploadedVideo(),
                    YouTubeVideoEntity.archivedStream(),
                )
                val channels = video.map { it.channel as YouTubeChannelTable }.distinctBy { it.id }
                dao.addChannels(channels)
                // exercise
                sut.addVideo(video)
                // verify
                val found = dao.findVideosById(video.map { it.id }, Instant.ofEpochMilli(10))
                assertThat(found).hasSize(3)
                assertThat(found.map { it.id }).containsExactlyInAnyOrderElementsOf(target.map { it.id })
                dao.watchAllUnfinishedVideos().test {
                    assertThat(awaitItem()).hasSize(3)
                }
                assertThat(dao.findAllArchivedVideos()).isEmpty()
                assertThat(dao.findUnusedVideoIds()).hasSize(3)
            }

        @Test
        fun setPlaylistItemsByPlaylistId_addedWithEmptyItems_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val id = YouTubePlaylist.Id("test")
                // exercise
                sut.setPlaylistItemsByPlaylistId(id, emptyList())
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
                assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
                assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
            }

        @Test
        fun setPlaylistItemsByPlaylistId_addedWithNull_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val id = YouTubePlaylist.Id("test")
                // exercise
                sut.setPlaylistItemsByPlaylistId(id, null)
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
                assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
                assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
            }

        @Test
        fun setPlaylistItemsByPlaylistId_addedWithItems_returnsItems() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val playlistId = YouTubePlaylist.Id("test")
                val items = listOf(
                    YouTubePlaylistItemEntity(
                        id = YouTubePlaylistItem.Id("playlist"),
                        playlistId = playlistId,
                        videoId = YouTubeVideo.Id("video"),
                    ),
                )
                val channel = items.map { it.channel as YouTubeChannelTable }.distinctBy { it.id }
                dao.addChannels(channel)
                // exercise
                sut.setPlaylistItemsByPlaylistId(playlistId, items)
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(playlistId)).hasSize(1)
                assertThat(dao.findPlaylistById(playlistId)?.id).isEqualTo(playlistId)
                assertThat(dao.findPlaylistItemSummary(playlistId, 10)).hasSize(1)
            }
    }

    class SimpleFind {
        @get:Rule
        internal val rule = DatabaseTestRule()
        private val live = YouTubeVideoEntity.liveStreaming()
        private val unscheduled = YouTubeVideoEntity.unscheduledUpcoming()
        private val upcoming = YouTubeVideoEntity.upcomingStream()
        private val video = listOf(
            live, upcoming, unscheduled,
            YouTubeVideoEntity.uploadedVideo(),
            YouTubeVideoEntity.archivedStream(),
        )

        @Before
        fun setup() = rule.runWithLocalSource(DateTimeProviderFake()) { dao, sut ->
            val channels = video.map { it.channel as YouTubeChannelTable }.distinctBy { it.id }
            dao.addChannels(channels)
            sut.addVideo(video)
        }

        @Test
        fun fetchVideo_returns3items() =
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochMilli(99))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).hasSize(3)
                assertThat(actual.map { it.id })
                    .containsExactlyInAnyOrder(live.id, upcoming.id, unscheduled.id)
            }

        @Test
        fun fetchVideo_upcomingExpiresAt100ms() {
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochMilli(100))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).hasSize(2)
                assertThat(actual.map { it.id }).containsExactlyInAnyOrder(live.id, unscheduled.id)
            }
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochMilli(60 * 1000 - 1))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).hasSize(2)
                assertThat(actual.map { it.id }).containsExactlyInAnyOrder(live.id, unscheduled.id)
            }
        }

        @Test
        fun fetchVideo_liveExpiresAt1min() {
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochSecond(60))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).hasSize(1)
                assertThat(actual.map { it.id }).containsExactlyInAnyOrder(unscheduled.id)
            }
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochMilli(10 * 60 * 1000 - 1))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).hasSize(1)
                assertThat(actual.map { it.id }).containsExactlyInAnyOrder(unscheduled.id)
            }
        }

        @Test
        fun fetchVideo_unscheduledExpiresAt10min() =
            rule.runWithLocalSource(DateTimeProviderFake(Instant.ofEpochSecond(10 * 60))) { _, sut ->
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).isEmpty()
            }
    }
}

private class DateTimeProviderFake(private val value: Instant = Instant.EPOCH) : DateTimeProvider {
    override fun now(): Instant = value
}

private data class YouTubeVideoEntity(
    override val id: YouTubeVideo.Id,
    override val title: String = "title",
    override val channel: YouTubeChannel = channelTable(),
    override val thumbnailUrl: String = "",
    override val scheduledStartDateTime: Instant? = null,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val description: String = "",
    override val viewerCount: BigInteger? = null,
    override val liveBroadcastContent: YouTubeVideo.BroadcastType?
) : YouTubeVideo {
    override fun needsUpdate(current: Instant): Boolean = false

    companion object {
        fun uploadedVideo(id: String = "uploaded_video"): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        )

        fun archivedStream(
            id: String = "archived_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(20),
            actualStartDateTime: Instant = Instant.ofEpochMilli(20),
            actualEndDateTime: Instant = Instant.ofEpochSecond(10 * 60),
        ): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            actualEndDateTime = actualEndDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        )

        fun liveStreaming(
            id: String = "live_streaming",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(50),
            actualStartDateTime: Instant = Instant.ofEpochMilli(50),
        ): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
        )

        fun upcomingStream(
            id: String = "upcoming_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(100),
        ): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        )

        fun unscheduledUpcoming(id: String = "unscheduled_upcoming"): YouTubeVideoEntity =
            YouTubeVideoEntity(
                id = YouTubeVideo.Id(id),
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
    }
}

private fun channelTable(
    id: YouTubeChannel.Id = YouTubeChannel.Id("channel"),
    title: String = "title",
    iconUrl: String = "",
): YouTubeChannelTable = YouTubeChannelTable(id, title, iconUrl)

private data class YouTubePlaylistItemEntity(
    override val id: YouTubePlaylistItem.Id,
    override val playlistId: YouTubePlaylist.Id,
    override val title: String = "title",
    override val channel: YouTubeChannel = channelTable(),
    override val thumbnailUrl: String = "",
    override val videoId: YouTubeVideo.Id,
    override val description: String = "",
    override val videoOwnerChannelId: YouTubeChannel.Id? = null,
    override val publishedAt: Instant = Instant.EPOCH,
) : YouTubePlaylistItem
