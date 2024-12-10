package com.freshdigitable.yttt.data.source.local

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.db.DatabaseTestRule
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
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

    class SimpleFindVideo {
        @get:Rule
        internal val rule = DatabaseTestRule()
        private val live = YouTubeVideoEntity.liveStreaming()
        private val unscheduled = YouTubeVideoEntity.unscheduledUpcoming()
        private val upcoming = YouTubeVideoEntity.upcomingStream()
        private val upcomingSoon = YouTubeVideoEntity.upcomingStream(
            id = "upcoming_soon",
            scheduledStartDateTime = Instant.ofEpochSecond(1500),
        )
        private val freeChat = YouTubeVideoEntity.freeChat()
        private val video = listOf(
            live, upcomingSoon, upcoming, unscheduled, freeChat,
            YouTubeVideoEntity.uploadedVideo(),
            YouTubeVideoEntity.archivedStream(),
        )
        private val base = Instant.ofEpochSecond(1000)
        private val datetimeProvider = DateTimeProviderFake(base)

        @Before
        fun setup() = rule.runWithLocalSource(datetimeProvider) { dao, sut ->
            val channels = video.map { it.channel as YouTubeChannelTable }.distinctBy { it.id }
            dao.addChannels(channels)
            sut.addVideo(video)
        }

        @Test
        fun fetchVideo_returnsLiveAndUpcomingItem() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(Duration.ZERO, Duration.ofSeconds(60).minusMillis(1)).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    // verify
                    actual.containsVideoIdInAnyOrder(
                        live, upcomingSoon, upcoming, unscheduled, freeChat,
                    )
                }
            }

        @Test
        fun fetchVideo_liveExpiresAfter1min() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(500).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    // verify
                    actual.containsVideoIdInAnyOrder(upcomingSoon, upcoming, unscheduled, freeChat)
                }
            }

        @Test
        fun fetchVideo_upcomingSoonExpiresAfter500sec() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofSeconds(500),
                    Duration.ofSeconds(10 * 60).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    // verify
                    actual.containsVideoIdInAnyOrder(upcoming, unscheduled, freeChat)
                }
            }

        @Test
        fun fetchVideo_upcomingExpiresAfter10min() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofMinutes(10),
                    Duration.ofDays(1).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    // verify
                    actual.containsVideoIdInAnyOrder(freeChat)
                }
            }

        @Test
        fun fetchVideo_freeChatExpiresAfter1day() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                datetimeProvider.setValue(base + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                // verify
                assertThat(actual).isEmpty()
            }

    }

    private companion object {
        private fun List<YouTubeVideo>.containsVideoIdInAnyOrder(vararg expected: YouTubeVideo) {
            assertThat(this).hasSize(expected.size)
            assertThat(this.map { it.id }).containsExactlyInAnyOrderElementsOf(expected.map { it.id })
        }
    }

    class SimpleFindPlaylistItems {
        @get:Rule
        internal val rule = DatabaseTestRule()
        private val baseTime = Instant.EPOCH
        private val dateTimeProvider = DateTimeProviderFake(baseTime)

        private val simple = YouTubePlaylist.Id("simple")
        private val privatePlaylist = YouTubePlaylist.Id("private")
        private val empty = YouTubePlaylist.Id("empty")
        private val items = mapOf(
            simple to listOf(
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id("playlist"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video"),
                ),
            ),
            privatePlaylist to null,
            empty to emptyList()
        )
        private val channel = items.values.mapNotNull { it }.flatten()
            .map { it.channel as YouTubeChannelTable }.distinctBy { it.id }

        @Before
        fun setup() = rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
            dao.addChannels(channel)
            items.forEach { (playlistId, items) ->
                sut.setPlaylistItemsByPlaylistId(playlistId, items)
            }
        }

        @Test
        fun fetchPlaylistItems_simple_has1item() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofMinutes(10).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistItems(simple)
                    // verify
                    assertThat(actual).hasSize(1)
                }
            }

        @Test
        fun fetchPlaylistItems_simpleExpiresAfter10min_returnsNull() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofMinutes(10))
                // exercise
                val actual = sut.fetchPlaylistItems(simple)
                // verify
                assertThat(actual).isNull()
            }

        @Test
        fun fetchPlaylistItems_simple_addSameItemBefore10min_returns1item() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                sut.setPlaylistItemsByPlaylistId(simple, checkNotNull(items[simple]))
                dateTimeProvider.advance(Duration.ofMinutes(20).minusMillis(1))
                // exercise
                val actual = sut.fetchPlaylistItems(simple)
                // verify
                assertThat(actual).hasSize(1)
            }

        @Test
        fun fetchPlaylistItems_simple_addSameItemBefore10min_expiresAfter20minReturnsNull() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                sut.setPlaylistItemsByPlaylistId(simple, checkNotNull(items[simple]))
                dateTimeProvider.advance(Duration.ofMinutes(20))
                // exercise
                val actual = sut.fetchPlaylistItems(simple)
                // verify
                assertThat(actual).isNull()
            }

        @Test
        fun fetchPlaylistItems_simple_addNewItemsBefore10min_returns2item() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                sut.setPlaylistItemsByPlaylistId(
                    simple, listOf(
                        YouTubePlaylistItemEntity(
                            id = YouTubePlaylistItem.Id("item2"),
                            playlistId = simple,
                            videoId = YouTubeVideo.Id("video_item2"),
                        )
                    ) + checkNotNull(items[simple])
                )
                dateTimeProvider.advance(Duration.ofMinutes(10).minusMillis(1))
                // exercise
                val actual = sut.fetchPlaylistItems(simple)
                // verify
                assertThat(actual).hasSize(2)
            }

        @Test
        fun fetchPlaylistItems_simple_addNewItemsBefore10min_expiresAfter10minReturnsNull() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                sut.setPlaylistItemsByPlaylistId(
                    simple, listOf(
                        YouTubePlaylistItemEntity(
                            id = YouTubePlaylistItem.Id("item2"),
                            playlistId = simple,
                            videoId = YouTubeVideo.Id("video_item2"),
                        )
                    ) + checkNotNull(items[simple])
                )
                dateTimeProvider.advance(Duration.ofMinutes(10))
                // exercise
                val actual = sut.fetchPlaylistItems(simple)
                // verify
                assertThat(actual).isNull()
            }

        @Test
        fun fetchPlaylistItems_private_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofDays(1).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistItems(privatePlaylist)
                    // verify
                    assertThat(actual).isEmpty()
                }
            }

        @Test
        fun fetchPlaylistItems_privateExpiresAfter1day_returnsNull() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistItems(privatePlaylist)
                // verify
                assertThat(actual).isNull()
            }

        @Test
        fun fetchPlaylistItems_empty_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofDays(1).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistItems(empty)
                    // verify
                    assertThat(actual).isEmpty()
                }
            }

        @Test
        fun fetchPlaylistItems_emptyExpiresAfter1day_returnsNull() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistItems(empty)
                // verify
                assertThat(actual).isNull()
            }

        @Test
        fun fetchPlaylistItemSummary_simple_returnsItems() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofMinutes(10))
                // exercise
                val actual = sut.fetchPlaylistItemSummary(simple, 10)
                // verify
                assertThat(actual).hasSize(1)
            }

        @Test
        fun fetchPlaylistItemSummary_private_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistItemSummary(privatePlaylist, 10)
                // verify
                assertThat(actual).isEmpty()
            }

        @Test
        fun fetchPlaylistItemSummary_empty_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistItemSummary(empty, 10)
                // verify
                assertThat(actual).isEmpty()
            }
    }

    class CleanUp {
        @get:Rule
        internal val rule = DatabaseTestRule()

        @Test
        fun cleanUp() = rule.runWithLocalSource(DateTimeProviderFake()) { dao, sut ->
            // setup
            val playlistId = YouTubePlaylist.Id("playlist")
            val upcoming = YouTubeVideoEntity.upcomingStream()
            val live = YouTubeVideoEntity.liveStreaming()
            val archivedInPlaylist = YouTubeVideoEntity.archivedStream()
            val videosInPlaylist = listOf(upcoming, live, archivedInPlaylist)
            sut.setPlaylistItemsByPlaylistId(
                playlistId,
                videosInPlaylist.map {
                    YouTubePlaylistItemEntity(
                        id = YouTubePlaylistItem.Id(it.id.value),
                        playlistId = playlistId,
                        videoId = it.id,
                    )
                },
            )
            val unusedArchive = YouTubeVideoEntity.archivedStream(id = "unused_archive")
            val freeChat = YouTubeVideoEntity.freeChat()
            val videos = videosInPlaylist + listOf(unusedArchive, freeChat)
            val channels = videos.map { it.channel as YouTubeChannelTable }
                .distinctBy { it.id }.toList()
            dao.addChannels(channels)
            sut.addVideo(videos)
            sut.addFreeChatItems(setOf(freeChat.id))
            // exercise
            sut.cleanUp()
            // verify
            assertThat(dao.findAllArchivedVideos()).isEmpty()
            assertThat(dao.findUnusedVideoIds()).isEmpty()
            val actual = dao.findVideosById(videos.map { it.id }, Instant.EPOCH)
            actual.containsVideoIdInAnyOrder(upcoming, live, freeChat)
            assertThat(rule.queryVideoIsArchived().map { it.videoId })
                .containsExactlyInAnyOrder(upcoming.id, live.id, freeChat.id, archivedInPlaylist.id)
        }

        private fun DatabaseTestRule.queryVideoIsArchived(): List<YouTubeVideoIsArchivedTable> {
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

private class DateTimeProviderFake(value: Instant = Instant.EPOCH) : DateTimeProvider {
    private var _value = value
    fun setValue(value: Instant) {
        _value = value
    }

    fun advance(value: Duration) {
        _value += value
    }

    override fun now(): Instant = _value
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
    override val isFreeChat: Boolean? = null,
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
            actualStartDateTime: Instant = scheduledStartDateTime,
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
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(1000),
            actualStartDateTime: Instant = scheduledStartDateTime,
        ): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
        )

        fun upcomingStream(
            id: String = "upcoming_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(5000),
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

        fun freeChat(
            id: String = "free_chat",
            scheduledStartDateTime: Instant = Instant.EPOCH + Duration.ofDays(30),
        ): YouTubeVideoEntity = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = "free chat",
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            isFreeChat = true,
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
