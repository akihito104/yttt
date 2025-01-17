package com.freshdigitable.yttt.data.source.local

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Updatable.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity.Companion.liveFinished
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
                val channels = video.map { it.channel as YouTubeChannelTable }.distinctBy { it.id }
                dao.addChannels(channels)
                // exercise
                sut.addVideo(video)
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
        fun replacePlaylistItemsWithUpdatable_addedWithEmptyItems_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val id = YouTubePlaylist.Id("test")
                val updatable = YouTubePlaylistWithItems.newPlaylist(
                    playlist = playlist(id),
                    items = emptyList(),
                    fetchedAt = dateTimeProvider.now(),
                )
                // exercise
                sut.updatePlaylistWithItems(updatable)
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
                assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
                assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
            }

        @Test
        fun replacePlaylistItemsWithUpdatable_addedWithNull_returnsEmpty() =
            rule.runWithLocalSource(dateTimeProvider) { dao, sut ->
                // setup
                val id = YouTubePlaylist.Id("test")
                val updatable = YouTubePlaylistWithItems.newPlaylist(
                    playlist = playlist(id),
                    items = null,
                    fetchedAt = dateTimeProvider.now(),
                )
                // exercise
                sut.updatePlaylistWithItems(updatable)
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(id)).isEmpty()
                assertThat(dao.findPlaylistById(id)?.id).isEqualTo(id)
                assertThat(dao.findPlaylistItemSummary(id, 10)).isEmpty()
            }

        @Test
        fun replacePlaylistItemsWithUpdatable_addedWithItems_returnsItems() =
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
                val updatable = YouTubePlaylistWithItems.newPlaylist(
                    playlist = playlist(playlistId),
                    items = items,
                    fetchedAt = dateTimeProvider.now(),
                )
                // exercise
                sut.updatePlaylistWithItems(updatable)
                // verify
                assertThat(dao.findPlaylistItemByPlaylistId(playlistId)).hasSize(1)
                assertThat(dao.findPlaylistById(playlistId)?.id).isEqualTo(playlistId)
                assertThat(dao.findPlaylistItemSummary(playlistId, 10)).hasSize(1)
            }
    }

    class SimpleFindVideo {
        @get:Rule
        internal val rule = DatabaseTestRule()
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
                listOf(Duration.ZERO, Duration.ofSeconds(300).minusMillis(1)).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    // verify
                    actual.containsVideoIdInAnyOrderElementsOf(video)
                }
            }

        @Test
        fun fetchVideo_liveExpiresAfter5min() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(500).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                        .filter { !it.isUpdatable(datetimeProvider.now()) }
                    // verify
                    actual.containsVideoIdInAnyOrder(
                        upcomingSoon, upcoming, unscheduled, freeChat, *archived.toTypedArray(),
                    )
                }
            }

        @Test
        fun fetchVideo_upcomingSoonExpiresAfter500sec() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofSeconds(500),
                    Duration.ofSeconds(20 * 60).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                        .filter { !it.isUpdatable(datetimeProvider.now()) }
                    // verify
                    actual.containsVideoIdInAnyOrder(
                        upcoming, unscheduled, freeChat, *archived.toTypedArray(),
                    )
                }
            }

        @Test
        fun fetchVideo_upcomingExpiresAfter20min() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                listOf(
                    Duration.ofMinutes(20),
                    Duration.ofDays(1).minusMillis(1),
                ).forEach { datetime ->
                    datetimeProvider.setValue(base + datetime)
                    // exercise
                    val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                        .filter { !it.isUpdatable(datetimeProvider.now()) }
                    // verify
                    actual.containsVideoIdInAnyOrder(freeChat, *archived.toTypedArray())
                }
            }

        @Test
        fun fetchVideo_freeChatExpiresAfter1day() =
            rule.runWithLocalSource(datetimeProvider) { _, sut ->
                // setup
                datetimeProvider.setValue(base + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchVideoList(video.map { it.id }.toSet())
                    .filter { !it.isUpdatable(datetimeProvider.now()) }
                // verify
                actual.containsVideoIdInAnyOrderElementsOf(archived)
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
            items.map { (playlistId, items) ->
                YouTubePlaylistWithItems.newPlaylist(
                    playlist = playlist(playlistId),
                    items = items,
                    fetchedAt = dateTimeProvider.now(),
                )
            }.forEach { sut.updatePlaylistWithItems(it) }
        }

        @Test
        fun fetchPlaylistWithItems_simple_isNotUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofMinutes(10).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistWithItems(simple)
                    // verify
                    assertThat(actual?.isUpdatable(dateTimeProvider.now())).isFalse()
                    assertThat(actual?.items).hasSize(1)
                }
            }

        @Test
        fun fetchPlaylistWithItems_simple_ExpiresAfter10min_isUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofMinutes(10))
                // exercise
                val actual = sut.fetchPlaylistWithItems(simple)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isTrue()
                assertThat(actual?.items).hasSize(1)
            }

        @Test
        fun fetchPlaylistWithItems_simple_addSameItemBefore10min_isNotUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                val updatable = sut.fetchPlaylistWithItems(simple)!!
                    .update(checkNotNull(items[simple]), dateTimeProvider.now())
                sut.updatePlaylistWithItems(updatable)
                dateTimeProvider.advance(Duration.ofMinutes(20).minusMillis(1))
                // exercise
                val actual = sut.fetchPlaylistWithItems(simple)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isFalse()
                assertThat(actual?.items).hasSize(1)
            }

        @Test
        fun fetchPlaylistWithItems_simple_addSameItemBefore10min_expiresAfter20min_isUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                val updatable = sut.fetchPlaylistWithItems(simple)!!
                    .update(checkNotNull(items[simple]), dateTimeProvider.now())
                sut.updatePlaylistWithItems(updatable)
                dateTimeProvider.advance(Duration.ofMinutes(20))
                // exercise
                val actual = sut.fetchPlaylistWithItems(simple)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isTrue()
                assertThat(actual?.items).hasSize(1)
            }

        @Test
        fun fetchPlaylistWithItems_simple_addNewItemsBefore10min_isNotUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                val newItems = listOf(
                    YouTubePlaylistItemEntity(
                        id = YouTubePlaylistItem.Id("item2"),
                        playlistId = simple,
                        videoId = YouTubeVideo.Id("video_item2"),
                    )
                ) + checkNotNull(items[simple])
                val updatable = sut.fetchPlaylistWithItems(simple)!!
                    .update(newItems, dateTimeProvider.now())
                sut.updatePlaylistWithItems(updatable)
                dateTimeProvider.advance(Duration.ofMinutes(10).minusMillis(1))
                // exercise
                val actual = sut.fetchPlaylistWithItems(simple)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isFalse()
                assertThat(actual?.items).hasSize(2)
            }

        @Test
        fun fetchPlaylistWithItems_simple_addNewItemsBefore10min_expiresAfter10min_isUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                val newItems = listOf(
                    YouTubePlaylistItemEntity(
                        id = YouTubePlaylistItem.Id("item2"),
                        playlistId = simple,
                        videoId = YouTubeVideo.Id("video_item2"),
                    )
                ) + checkNotNull(items[simple])
                val updatable = sut.fetchPlaylistWithItems(simple)!!
                    .update(newItems, dateTimeProvider.now())
                sut.updatePlaylistWithItems(updatable)
                dateTimeProvider.advance(Duration.ofMinutes(10))
                // exercise
                val actual = sut.fetchPlaylistWithItems(simple)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isTrue()
                assertThat(actual?.items).hasSize(2)
            }

        @Test
        fun fetchPlaylistWithItems_private_isNotUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofDays(1).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistWithItems(privatePlaylist)
                    // verify
                    assertThat(actual?.isUpdatable(dateTimeProvider.now())).isFalse()
                    assertThat(actual?.items).isEmpty()
                }
            }

        @Test
        fun fetchPlaylistWithItems_privateExpiresAfter1day_isUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistWithItems(privatePlaylist)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isTrue()
                assertThat(actual?.items).isEmpty()
            }

        @Test
        fun fetchPlaylistWithItems_empty_isNotUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                listOf(Duration.ZERO, Duration.ofDays(1).minusMillis(1)).forEach {
                    // setup
                    dateTimeProvider.setValue(baseTime + it)
                    // exercise
                    val actual = sut.fetchPlaylistWithItems(empty)
                    // verify
                    assertThat(actual?.isUpdatable(dateTimeProvider.now())).isFalse()
                    assertThat(actual?.items).isEmpty()
                }
            }

        @Test
        fun fetchPlaylistWithItems_emptyExpiresAfter1day_isUpdatable() =
            rule.runWithLocalSource(dateTimeProvider) { _, sut ->
                // setup
                dateTimeProvider.setValue(baseTime + Duration.ofDays(1))
                // exercise
                val actual = sut.fetchPlaylistWithItems(empty)
                // verify
                assertThat(actual?.isUpdatable(dateTimeProvider.now())).isTrue()
                assertThat(actual?.items).isEmpty()
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
        private val datetimeProvider = DateTimeProviderFake()
        private val upcoming = YouTubeVideoEntity.upcomingStream()
        private val live = YouTubeVideoEntity.liveStreaming()
        private val archivedInPlaylist = YouTubeVideoEntity.archivedStream()
        private val videosInPlaylist = listOf(upcoming, live, archivedInPlaylist)
        private val freeChat = YouTubeVideoEntity.freeChat()
        private val unusedArchive = YouTubeVideoEntity.archivedStream(id = "unused_archive")
        private val endlessLive = YouTubeVideoEntity.liveStreaming(id = "endless_live")
        private val videos = videosInPlaylist + listOf(unusedArchive, freeChat, endlessLive)

        @Before
        fun setup() = rule.runWithLocalSource(datetimeProvider) { dao, sut ->
            val playlistId = YouTubePlaylist.Id("playlist")
            val items = videosInPlaylist.map {
                YouTubePlaylistItemEntity(
                    id = YouTubePlaylistItem.Id(it.id.value),
                    playlistId = playlistId,
                    videoId = it.id,
                )
            }
            val updatable = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = items,
                fetchedAt = datetimeProvider.now(),
            )
            sut.updatePlaylistWithItems(updatable)
            val channels = videos.map { it.channel as YouTubeChannelTable }
                .distinctBy { it.id }.toList()
            dao.addChannels(channels)
            sut.addVideo(videos)
        }

        @Test
        fun cleanUp_remainsUnfinishedVideos() =
            rule.runWithLocalSource(datetimeProvider) { dao, sut ->
                // exercise
                sut.cleanUp()
                // verify
                assertThat(dao.findAllArchivedVideos()).isEmpty()
                assertThat(dao.findUnusedVideoIds()).isEmpty()
                val actual = dao.findVideosById(videos.map { it.id })
                actual.containsVideoIdInAnyOrder(upcoming, live, freeChat, endlessLive)
                assertThat(rule.queryVideoIsArchived().map { it.videoId })
                    .containsExactlyInAnyOrder(archivedInPlaylist.id)
            }

        @Test
        fun cleanUp_liveIsUpdatedAsArchived_remainsUpcomingAndFreeChat() =
            rule.runWithLocalSource(datetimeProvider) { dao, sut ->
                // setup
                val duration = Duration.ofHours(1)
                datetimeProvider.advance(duration)
                val finishedLive = live.liveFinished(duration)
                sut.addVideo(listOf(finishedLive))
                // exercise
                sut.cleanUp()
                // verify
                assertThat(dao.findAllArchivedVideos()).isEmpty()
                assertThat(dao.findUnusedVideoIds()).isEmpty()
                val actual = dao.findVideosById(videos.map { it.id })
                actual.containsVideoIdInAnyOrder(upcoming, freeChat, endlessLive)
                assertThat(rule.queryVideoIsArchived().map { it.videoId })
                    .containsExactlyInAnyOrder(live.id, archivedInPlaylist.id)
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
) : YouTubeVideo {
    companion object {
        fun uploadedVideo(
            id: String = "uploaded_video",
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        ).extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)

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
        ).extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)

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
        ).extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)

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
        ).extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)

        fun unscheduledUpcoming(
            id: String = "unscheduled_upcoming",
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).extend(old = null, isFreeChat = false, fetchedAt = fetchedAt)

        fun freeChat(
            id: String = "free_chat",
            scheduledStartDateTime: Instant = Instant.EPOCH + Duration.ofDays(30),
            fetchedAt: Instant = Instant.EPOCH,
        ): YouTubeVideoExtended = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = "free chat",
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).extend(old = null, isFreeChat = true, fetchedAt = fetchedAt)
    }
}

private fun channelTable(
    id: YouTubeChannel.Id = YouTubeChannel.Id("channel"),
    title: String = "title",
    iconUrl: String = "",
): YouTubeChannelTable = YouTubeChannelTable(id, title, iconUrl)

private fun playlist(playlistId: YouTubePlaylist.Id): YouTubePlaylist = object : YouTubePlaylist {
    override val id: YouTubePlaylist.Id = playlistId
    override val title: String = ""
    override val thumbnailUrl: String = ""
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
) : YouTubePlaylistItem
