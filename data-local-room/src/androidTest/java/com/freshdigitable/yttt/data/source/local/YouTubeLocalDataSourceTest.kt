package com.freshdigitable.yttt.data.source.local

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity.Companion.liveFinished
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import com.freshdigitable.yttt.data.source.local.db.toDbEntity
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
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
                awaitItem().shouldBeEmpty()
            }
        }

        @Test
        fun videoIsEmpty() = rule.runWithLocalSource {
            dataSource.fetchVideoList(emptySet()).getOrNull().shouldBeEmpty()
            dataSource.fetchVideoList(setOf(YouTubeVideo.Id("test"))).getOrNull().shouldBeEmpty()
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
            val channels = video.map { it.item.channel.toDbEntity() }.distinctBy { it.id }
            dao.addChannels(channels)
            // exercise
            dataSource.addVideo(video)
            // verify
            val found = dao.findVideosById(video.map { it.item.id })
            found.containsVideoIdInAnyOrderElementsOf(video)
            dao.watchAllUnfinishedVideos().test {
                awaitItem().containsVideoIdInAnyOrder(*unfinished.map { it.item }
                    .toTypedArray())
            }
            dao.findAllArchivedVideos()
                .shouldContainExactlyInAnyOrder(inactive.map { it.item.id })
            dao.findUnusedVideoIds().shouldContainExactlyInAnyOrder(inactive.map { it.item.id })
        }
    }

    class SimpleFetchVideo {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()
        private val base = Instant.ofEpochSecond(1000)
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
            val channels = video.map { it.item.channel.toDbEntity() }.distinctBy { it.id }
            dao.addChannels(channels)
            dataSource.addVideo(video)
        }

        @Test
        fun returnsAllItems() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchVideoList(video.map { it.item.id }.toSet()).getOrThrow()
            // verify
            actual.containsVideoIdInAnyOrderElementsOf(video)
        }

        @Test
        fun withUnknownKey_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual =
                dataSource.fetchVideoList(setOf(YouTubeVideo.Id("unknown_entity"))).getOrNull()
            // verify
            actual.shouldBeEmpty()
        }

        @Test
        fun withFreeChat_returns1Item() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchVideoList(setOf(freeChat.item.id)).getOrNull()
                ?: throw AssertionError()
            // verify
            actual.size shouldBe 1
            actual.first().item.isFreeChat shouldBe true
        }
    }

    private companion object {
        private fun List<Updatable<YouTubeVideoExtended>>.containsVideoIdInAnyOrderElementsOf(
            expected: Collection<Updatable<YouTubeVideoExtended>>,
        ) {
            containsVideoIdInAnyOrder(*expected.toTypedArray())
        }

        private fun List<Updatable<YouTubeVideoExtended>>.containsVideoIdInAnyOrder(vararg expected: Updatable<YouTubeVideoExtended>) {
            this.map { it.item }.containsVideoIdInAnyOrder(*expected.map { it.item }.toTypedArray())
        }

        private fun List<YouTubeVideoExtended>.containsVideoIdInAnyOrder(vararg expected: YouTubeVideoExtended) {
            this.size shouldBe expected.size
            this.map { it.id }.shouldContainExactlyInAnyOrder(expected.map { it.id })
        }
    }

    class UpdatePlaylistWithItems {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()

        @Test
        fun addedWithEmptyItems_returnsEmpty() = rule.runWithLocalSource {
            // setup
            val id = YouTubePlaylist.Id("test")
            val updatable = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(id),
                items = emptyList<YouTubePlaylistItemDetail>()
                    .toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            // exercise
            dataSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(id).shouldBeEmpty()
            dao.findPlaylistById(id)?.id shouldBe id
        }

        @Test
        fun addedWithItems_returnsItems() = rule.runWithLocalSource {
            // setup
            val playlistId = YouTubePlaylist.Id("test")
            val items = listOf(
                FakeYouTubeClient.playlistItemDetail(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = playlistId,
                    videoId = YouTubeVideo.Id("video"),
                    channel = channelTable(),
                ),
            )
            val channel = items.map { (it.channel as YouTubeChannel).toDbEntity() }
                .distinctBy { it.id }
            dao.addChannels(channel)
            val updatable = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            )
            // exercise
            dataSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(playlistId).size shouldBe 1
            dao.findPlaylistById(playlistId)?.id shouldBe playlistId
        }

        @Test
        fun itemWasReplaced_returnsItems() = rule.runWithLocalSource {
            // setup
            val playlistId = YouTubePlaylist.Id("test")
            val items = listOf(
                FakeYouTubeClient.playlistItemDetail(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = playlistId,
                    videoId = YouTubeVideo.Id("video"),
                    channel = channelTable(),
                ),
            )
            val channel = items.map { (it.channel as YouTubeChannel).toDbEntity() }
                .distinctBy { it.id }
            dao.addChannels(channel)
            val updatable = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            )
            dataSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // exercise
            val u = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = listOf(
                    FakeYouTubeClient.playlistItemDetail(
                        id = YouTubePlaylistItem.Id("playlist_1"),
                        playlistId = playlistId,
                        channel = channelTable(),
                    )
                ).toUpdatable(Instant.EPOCH)
            )
            dataSource.updatePlaylistWithItems(u.item, u.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(playlistId).size shouldBe 1
            dao.findPlaylistById(playlistId)?.id shouldBe playlistId
        }
    }

    class SimpleFetchPlaylistWithItems {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()
        private val simple = YouTubePlaylist.Id("simple")
        private val privatePlaylist = YouTubePlaylist.Id("private")
        private val empty = YouTubePlaylist.Id("empty")
        private val items = mapOf(
            simple to listOf(
                FakeYouTubeClient.playlistItem(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video"),
                ),
            ),
            privatePlaylist to emptyList(),
            empty to emptyList()
        ).map { (playlistId, items) ->
            playlistId to YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            )
        }.toMap()

        @Before
        fun setup() = rule.runWithLocalSource {
            items.values.forEach { dataSource.updatePlaylistWithItems(it.item, it.cacheControl) }
        }

        @Test
        fun simple_returns1Item() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 1
        }

        @Test
        fun simple_addSameItem_returns1Item() = rule.runWithLocalSource {
            // setup
            val updatable = dataSource.fetchPlaylistWithItems(simple, 10).map {
                it?.item?.update(items[simple]!!.item.items.toUpdatable(fetchedAt = Instant.EPOCH))
            }.getOrNull()
            dataSource.updatePlaylistWithItems(updatable!!.item, updatable.cacheControl)
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 1
        }

        @Test
        fun simple_addNewItems_returns2Items() = rule.runWithLocalSource {
            // setup
            val newItems = listOf(
                FakeYouTubeClient.playlistItemDetail(
                    id = YouTubePlaylistItem.Id("item2"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video_item2"),
                    channel = channelTable(),
                )
            ) + checkNotNull(items[simple]!!.item.items)
            val updatable = dataSource.fetchPlaylistWithItems(simple, 10).map {
                it?.item?.update(newItems.toUpdatable(fetchedAt = Instant.EPOCH))
            }.getOrNull()
            dataSource.updatePlaylistWithItems(updatable!!.item, updatable.cacheControl)
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 2
        }

        @Test
        fun private_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(privatePlaylist, 10).getOrNull()
            // verify
            actual?.item?.items.shouldBeEmpty()
        }

        @Test
        fun empty_returnsEmpty() = rule.runWithLocalSource {
            // exercise
            val actual = dataSource.fetchPlaylistWithItems(empty, 10).getOrNull()
            // verify
            actual?.item?.items.shouldBeEmpty()
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
                FakeYouTubeClient.playlistItemDetail(
                    id = YouTubePlaylistItem.Id(it.item.id.value),
                    playlistId = playlistId,
                    videoId = it.item.id,
                    channel = channelTable(),
                )
            }
            val updatable = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            dataSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            val channels = videos.map { it.item.channel.toDbEntity() }
                .distinctBy { it.id }.toList()
            dao.addChannels(channels)
            dataSource.addVideo(videos)
        }

        @Test
        fun remainsUnfinishedVideos() = rule.runWithLocalSource {
            // exercise
            dataSource.cleanUp()
            // verify
            dao.findAllArchivedVideos().shouldBeEmpty()
            dao.findUnusedVideoIds().shouldBeEmpty()
            val actual = dao.findVideosById(videos.map { it.item.id })
            actual.containsVideoIdInAnyOrder(upcoming, live, freeChat, endlessLive)
            rule.queryVideoIsArchived().map { it.videoId }
                .shouldContainExactlyInAnyOrder(archivedInPlaylist.item.id)
        }

        @Test
        fun liveIsUpdatedAsArchived_remainsUpcomingAndFreeChat() = rule.runWithLocalSource {
            // setup
            val duration = Duration.ofHours(1)
            val finishedLive = live.item.liveFinished(duration)
            dataSource.addVideo(listOf(finishedLive))
            // exercise
            dataSource.cleanUp()
            // verify
            dao.findAllArchivedVideos().shouldBeEmpty()
            dao.findUnusedVideoIds().shouldBeEmpty()
            val actual = dao.findVideosById(videos.map { it.item.id })
            actual.containsVideoIdInAnyOrder(upcoming, freeChat, endlessLive)
            rule.queryVideoIsArchived().map { it.videoId }
                .shouldContainExactlyInAnyOrder(live.item.id, archivedInPlaylist.item.id)
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
) : YouTubeVideo {

    companion object {
        fun uploadedVideo(
            id: String = "uploaded_video",
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun archivedStream(
            id: String = "archived_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(20),
            actualStartDateTime: Instant = scheduledStartDateTime,
            actualEndDateTime: Instant = Instant.ofEpochSecond(10 * 60),
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            actualEndDateTime = actualEndDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun liveStreaming(
            id: String = "live_streaming",
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(1000),
            actualStartDateTime: Instant = scheduledStartDateTime,
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun YouTubeVideo.liveFinished(
            duration: Duration = Duration.ofHours(1),
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> {
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
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun unscheduledUpcoming(
            id: String = "unscheduled_upcoming",
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun freeChat(
            id: String = "free_chat",
            scheduledStartDateTime: Instant = Instant.EPOCH + Duration.ofDays(30),
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = "free chat",
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = true)
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
): Updatable<YouTubePlaylist> = FakeYouTubeClient.playlist(playlistId)
    .toUpdatable(cacheControl = CacheControl.fromRemote(fetchedAt))
