package com.freshdigitable.yttt.data.source.local

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity.Companion.liveFinished
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDatabaseTestRule
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import com.freshdigitable.yttt.data.source.local.db.toDbEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubeLocalDataSourceTest {
    class Init {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()

        @Test
        fun videoFlowIsEmpty() = rule.runWithLocalSource {
            dataSource.videos.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun videoIsEmpty() = rule.runWithLocalSource {
            assertThat(dataSource.fetchVideoList(emptySet()).getOrNull()).isEmpty()
            assertThat(dataSource.fetchVideoList(setOf(YouTubeVideo.Id("test"))).getOrNull())
                .isEmpty()
        }

        @Test
        fun addVideo_addedLiveAndUpcomingItems() = rule.runWithLocalSource {
            // setup
            val unfinished = listOf(
                YouTubeVideoEntity.liveStreaming(),
                YouTubeVideoEntity.upcomingStream(),
                YouTubeVideoEntity.unscheduledUpcoming(),
            )
            val inactive = listOf(
                YouTubeVideoEntity.uploadedVideo(),
                YouTubeVideoEntity.archivedStream(),
            )
            val video = unfinished + inactive
            val channels = video.map { it.channel.toDbEntity() }.distinctBy { it.id }
            dao.addChannels(channels)
            // exercise
            dataSource.addVideo(video)
            // verify
            val found = dao.findVideosById(video.map { it.id })
            found.containsVideoIdInAnyOrderElementsOf(video)
            dao.watchAllUnfinishedVideos().test {
                awaitItem().containsVideoIdInAnyOrderElementsOf(unfinished)
            }
            assertThat(dao.findAllArchivedVideos()).containsExactlyInAnyOrderElementsOf(inactive.map { it.id })
            assertThat(dao.findUnusedVideoIds()).containsExactlyInAnyOrderElementsOf(inactive.map { it.id })
        }

        @Test
        fun updatePlaylistWithItems_addedWithEmptyItems_returnsEmpty() = rule.runWithLocalSource {
            // setup
            val id = YouTubePlaylist.Id("test")
            val updatable = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(id, dateTimeProvider.now()),
                items = emptyList(),
            )
            // exercise
            dataSource.updatePlaylistWithItems(updatable)
            // verify
            assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
            assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
            assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
        }

        @Test
        fun updatePlaylistWithItems_addedWithNull_returnsEmpty() = rule.runWithLocalSource {
            // setup
            val id = YouTubePlaylist.Id("test")
            val updatable = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(id, dateTimeProvider.now()),
                items = null,
            )
            // exercise
            dataSource.updatePlaylistWithItems(updatable)
            // verify
            assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
            assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
            assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
        }

        @Test
        fun updatePlaylistWithItems_addedWithItems_returnsItems() = rule.runWithLocalSource {
            // setup
            val playlistId = YouTubePlaylist.Id("test")
            val items = listOf(
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = playlistId,
                    videoId = YouTubeVideo.Id("video"),
                    fetchedAt = dateTimeProvider.now(),
                ),
            )
            val channel = items.map { it.channel.toDbEntity() }.distinctBy { it.id }
            dao.addChannels(channel)
            val updatable = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId, dateTimeProvider.now()),
                items = items,
            )
            // exercise
            dataSource.updatePlaylistWithItems(updatable)
            // verify
            assertThat(dao.findPlaylistItemByPlaylistId(playlistId)).hasSize(1)
            assertThat(dao.findPlaylistById(playlistId)?.id).isEqualTo(playlistId)
            assertThat(dao.findPlaylistItemSummary(playlistId, 10)).hasSize(1)
        }
    }

    class SimpleFindVideo {
        private val base = Instant.ofEpochSecond(1000)

        @get:Rule
        internal val rule = YouTubeDatabaseTestRule(base)
        private val live = YouTubeVideoEntity.liveStreaming(fetchedAt = base)
        private val unscheduled = YouTubeVideoEntity.unscheduledUpcoming(fetchedAt = base)
        private val upcoming = YouTubeVideoEntity.upcomingStream(fetchedAt = base)
        private val upcomingSoon = YouTubeVideoEntity.upcomingStream(
            id = "upcoming_soon",
            scheduledStartDateTime = Instant.ofEpochSecond(1500),
            fetchedAt = base,
        )
        private val freeChat = YouTubeVideoEntity.freeChat(fetchedAt = base)
        private val archived = listOf(
            YouTubeVideoEntity.uploadedVideo(fetchedAt = base),
            YouTubeVideoEntity.archivedStream(fetchedAt = base),
        )
        private val video = listOf(live, upcomingSoon, upcoming, unscheduled, freeChat) + archived

        @Before
        fun setup() = rule.runWithLocalSource {
            val channels = video.map { it.channel.toDbEntity() }.distinctBy { it.id }
            dao.addChannels(channels)
            dataSource.addVideo(video)
        }

        @Test
        fun fetchVideo_returnsAllItems() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchVideoList(video.map { it.id }.toSet()).getOrNull()
                ?: throw AssertionError()
            // verify
            actual.containsVideoIdInAnyOrderElementsOf(video)
        }

        @Test
        fun fetchVideo_withUnknownKey_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual =
                dataSource.fetchVideoList(setOf(YouTubeVideo.Id("unknown_entity"))).getOrNull()
            // verify
            assertThat(actual).isEmpty()
        }

        @Test
        fun fetchVideo_withFreeChat_returns1Item() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchVideoList(setOf(freeChat.id)).getOrNull()
                ?: throw AssertionError()
            // verify
            assertThat(actual).hasSize(1)
            assertThat(actual.first().isFreeChat).isTrue()
        }
    }

    private companion object {
        private fun List<YouTubeVideo>.containsVideoIdInAnyOrderElementsOf(expected: Collection<YouTubeVideo>) {
            containsVideoIdInAnyOrder(*expected.toTypedArray())
        }

        private fun List<YouTubeVideo>.containsVideoIdInAnyOrder(vararg expected: YouTubeVideo) {
            assertThat(this).hasSize(expected.size)
            assertThat(this.map { it.id }).containsExactlyInAnyOrderElementsOf(expected.map { it.id })
        }
    }

    class SimpleFindPlaylistWithItems {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()
        private val simple = YouTubePlaylist.Id("simple")
        private val privatePlaylist = YouTubePlaylist.Id("private")
        private val empty = YouTubePlaylist.Id("empty")
        private val items = mapOf(
            simple to listOf(
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video"),
                    fetchedAt = rule.dateTimeProvider.now(),
                ),
            ),
            privatePlaylist to null,
            empty to emptyList()
        ).map { (playlistId, items) ->
            playlistId to YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId, rule.dateTimeProvider.now()),
                items = items,
            )
        }.toMap()
        private val channel = items.values.map { it.items }.flatten()
            .map { it.channel as YouTubeChannelTable }.distinctBy { it.id }

        @Before
        fun setup() = rule.runWithLocalSource {
            dao.addChannels(channel)
            items.values.forEach { dataSource.updatePlaylistWithItems(it) }
        }

        @Test
        fun fetchPlaylistWithItems_simple_returns1Item() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            assertThat(actual?.items).hasSize(1)
        }

        @Test
        fun fetchPlaylistWithItems_simple_addSameItem_returns1Item() = rule.runWithLocalSource {
            // setup
            val updatable = dataSource.fetchPlaylistWithItems(simple, 10).map {
                it?.update(checkNotNull(items[simple]!!.items), dateTimeProvider.now())
            }.getOrNull()
            dataSource.updatePlaylistWithItems(updatable!!)
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            assertThat(actual?.items).hasSize(1)
        }

        @Test
        fun fetchPlaylistWithItems_simple_addNewItems_returns2Items() = rule.runWithLocalSource {
            // setup
            val newItems = listOf(
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id("item2"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video_item2"),
                    fetchedAt = dateTimeProvider.now(),
                )
            ) + checkNotNull(items[simple]!!.items)
            val updatable = dataSource.fetchPlaylistWithItems(simple, 10).map {
                it?.update(newItems, dateTimeProvider.now())
            }.getOrNull()
            dataSource.updatePlaylistWithItems(updatable!!)
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            assertThat(actual?.items).hasSize(2)
        }

        @Test
        fun fetchPlaylistWithItems_private_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(privatePlaylist, 10).getOrNull()
            // verify
            assertThat(actual?.items).isEmpty()
        }

        @Test
        fun fetchPlaylistWithItems_empty_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(empty, 10).getOrNull()
            // verify
            assertThat(actual?.items).isEmpty()
        }

        @Test
        fun fetchPlaylistItemSummary_simple_returns1Item() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistItemSummary(simple, 10)
            // verify
            assertThat(actual).hasSize(1)
        }

        @Test
        fun fetchPlaylistItemSummary_private_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistItemSummary(privatePlaylist, 10)
            // verify
            assertThat(actual).isEmpty()
        }

        @Test
        fun fetchPlaylistItemSummary_empty_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistItemSummary(empty, 10)
            // verify
            assertThat(actual).isEmpty()
        }
    }

    class CleanUp {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()
        private val upcoming = YouTubeVideoEntity.upcomingStream()
        private val live = YouTubeVideoEntity.liveStreaming()
        private val archivedInPlaylist = YouTubeVideoEntity.archivedStream()
        private val videosInPlaylist = listOf(upcoming, live, archivedInPlaylist)
        private val freeChat = YouTubeVideoEntity.freeChat()
        private val unusedArchive = YouTubeVideoEntity.archivedStream(id = "unused_archive")
        private val endlessLive = YouTubeVideoEntity.liveStreaming(id = "endless_live")
        private val videos = videosInPlaylist + listOf(unusedArchive, freeChat, endlessLive)

        @Before
        fun setup() = rule.runWithLocalSource {
            val playlistId = YouTubePlaylist.Id("playlist")
            val items = videosInPlaylist.map {
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id(it.id.value),
                    playlistId = playlistId,
                    videoId = it.id,
                    fetchedAt = rule.dateTimeProvider.now(),
                )
            }
            val updatable = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId, dateTimeProvider.now()),
                items = items,
            )
            dataSource.updatePlaylistWithItems(updatable)
            val channels = videos.map { it.channel.toDbEntity() }
                .distinctBy { it.id }.toList()
            dao.addChannels(channels)
            dataSource.addVideo(videos)
        }

        @Test
        fun cleanUp_remainsUnfinishedVideos() = rule.runWithLocalSource {
            // exercise
            dataSource.cleanUp()
            // verify
            assertThat(dao.findAllArchivedVideos()).isEmpty()
            assertThat(dao.findUnusedVideoIds()).isEmpty()
            val actual = dao.findVideosById(videos.map { it.id })
            actual.containsVideoIdInAnyOrder(upcoming, live, freeChat, endlessLive)
            assertThat(rule.queryVideoIsArchived().map { it.videoId })
                .containsExactlyInAnyOrder(archivedInPlaylist.id)
        }

        @Test
        fun cleanUp_liveIsUpdatedAsArchived_remainsUpcomingAndFreeChat() = rule.runWithLocalSource {
            // setup
            val duration = Duration.ofHours(1)
            dateTimeProvider.advance(duration)
            val finishedLive = live.liveFinished(duration)
            dataSource.addVideo(listOf(finishedLive))
            // exercise
            dataSource.cleanUp()
            // verify
            assertThat(dao.findAllArchivedVideos()).isEmpty()
            assertThat(dao.findUnusedVideoIds()).isEmpty()
            val actual = dao.findVideosById(videos.map { it.id })
            actual.containsVideoIdInAnyOrder(upcoming, freeChat, endlessLive)
            assertThat(rule.queryVideoIsArchived().map { it.videoId })
                .containsExactlyInAnyOrder(live.id, archivedInPlaylist.id)
        }

        private fun YouTubeDatabaseTestRule.queryVideoIsArchived(): List<YouTubeVideoIsArchivedTable> {
            return query("select * from yt_video_is_archived") {
                val videoIdIndex = it.getColumnIndex("video_id")
                val isArchivedIndex = it.getColumnIndex("is_archived")
                val res = ArrayList<YouTubeVideoIsArchivedTable>(it.count)
                while (it.moveToNext()) {
                    val id = it.getString(videoIdIndex)
                    val isArchived = it.getInt(isArchivedIndex) == 1
                    res.add(YouTubeVideoIsArchivedTable(YouTubeVideo.Id(id), isArchived))
                }
                res
            }
        }
    }
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
    override val liveBroadcastContent: YouTubeVideo.BroadcastType?,
    private val fetchedAt: Instant,
) : YouTubeVideo {
    override val cacheControl: CacheControl
        get() = CacheControl.create(fetchedAt, Duration.ofMinutes(5))

    companion object {
        fun uploadedVideo(
            id: String = "uploaded_video",
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = false)

        fun archivedStream(
            id: String = "archived_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(20),
            actualStartDateTime: Instant = scheduledStartDateTime,
            actualEndDateTime: Instant = Instant.ofEpochSecond(10 * 60),
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            actualEndDateTime = actualEndDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = false)

        fun liveStreaming(
            id: String = "live_streaming",
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(1000),
            actualStartDateTime: Instant = scheduledStartDateTime,
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = false)

        fun YouTubeVideo.liveFinished(
            duration: Duration = Duration.ofHours(1),
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended {
            check(liveBroadcastContent == YouTubeVideo.BroadcastType.LIVE)
            return archivedStream(
                id = id.value,
                scheduledStartDateTime = requireNotNull(scheduledStartDateTime),
                actualStartDateTime = requireNotNull(actualStartDateTime),
                actualEndDateTime = requireNotNull(actualStartDateTime) + duration,
                fetchedAt = fetchedAt,
            )
        }

        fun upcomingStream(
            id: String = "upcoming_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(5000),
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = false)

        fun unscheduledUpcoming(
            id: String = "unscheduled_upcoming",
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = false)

        fun freeChat(
            id: String = "free_chat",
            scheduledStartDateTime: Instant = Instant.EPOCH + Duration.ofDays(30),
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = "free chat",
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            fetchedAt = fetchedAt,
        ).extend(old = null, isFreeChat = true)
    }
}

private fun channelTable(
    id: YouTubeChannel.Id = YouTubeChannel.Id("channel"),
    title: String = "title",
    iconUrl: String = "",
): YouTubeChannelTable = YouTubeChannelTable(id, title, iconUrl)

private fun playlist(
    playlistId: YouTubePlaylist.Id,
    fetchedAt: Instant = Instant.EPOCH,
): YouTubePlaylist = object : YouTubePlaylist {
    override val id: YouTubePlaylist.Id = playlistId
    override val title: String = ""
    override val thumbnailUrl: String = ""
    override val cacheControl: CacheControl
        get() = CacheControl.create(fetchedAt, Duration.ofMinutes(5))
}

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
    private val maxAge: Duration? = Duration.ofMinutes(5),
    private val fetchedAt: Instant?,
) : YouTubePlaylistItem {
    override val cacheControl: CacheControl
        get() = CacheControl.create(fetchedAt, maxAge)
}
