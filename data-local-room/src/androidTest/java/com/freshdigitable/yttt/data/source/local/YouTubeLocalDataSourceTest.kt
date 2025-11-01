package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.update
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionRelevanceOrdered
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity.Companion.liveFinished
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule
import com.freshdigitable.yttt.data.source.local.fixture.findAllArchivedVideos
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
    class Init : Base() {
        @Test
        fun videoIsEmpty() = rule.runWithScope {
            extendedSource.fetchVideoList(emptySet()).getOrNull().shouldBeEmpty()
            extendedSource.fetchVideoList(setOf(YouTubeVideo.Id("test"))).getOrNull().shouldBeEmpty()
        }
    }

    class SimpleFetchVideo : Base() {
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
        fun setup() = rule.runWithScope {
            val channels = video.map { it.item.channel }.distinctBy { it.id }
            dao.addChannelEntities(channels)
            extendedSource.addVideo(video)
        }

        @Test
        fun returnsAllItems() = rule.runWithScope {
            // exercise
            val actual = extendedSource.fetchVideoList(video.map { it.item.id }.toSet()).getOrThrow()
            // verify
            actual.containsVideoIdInAnyOrderElementsOf(video)
        }

        @Test
        fun withUnknownKey_returnsEmpty() = rule.runWithScope {
            // exercise
            val actual = extendedSource.fetchVideoList(setOf(YouTubeVideo.Id("unknown_entity"))).getOrNull()
            // verify
            actual.shouldBeEmpty()
        }

        @Test
        fun withFreeChat_returns1Item() = rule.runWithScope {
            // exercise
            val actual = extendedSource.fetchVideoList(setOf(freeChat.item.id)).getOrNull()
                ?: throw AssertionError()
            // verify
            actual.size shouldBe 1
            actual.first().item.isFreeChat shouldBe true
        }
    }

    class UpdatePlaylistWithItems : Base() {
        @Test
        fun addedWithEmptyItems_returnsEmpty() = rule.runWithScope {
            // setup
            val id = YouTubePlaylist.Id("test")
            val updatable = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(id),
                items = emptyList<YouTubePlaylistItemDetail>()
                    .toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            // exercise
            extendedSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(id).shouldBeEmpty()
            dao.findPlaylistById(id)?.id shouldBe id
        }

        val playlistId = YouTubePlaylist.Id("test")
        val videoId = YouTubeVideo.Id("video")
        private val channel = channelTable()
        val items = listOf(
            FakeYouTubeClient.playlistItemDetail(
                id = YouTubePlaylistItem.Id("playlist"),
                playlistId = playlistId,
                videoId = videoId,
                channel = channel,
            ),
        )
        val channels = items.map { (it.channel as YouTubeChannel) }.distinctBy { it.id }
        val updatable = YouTubePlaylistWithItem.newPlaylist(
            playlist = playlist(playlistId),
            items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
        )

        @Test
        fun addedWithItems_returnsItems() = rule.runWithScope {
            // setup
            dao.addChannelEntities(channels)
            // exercise
            extendedSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(playlistId).size shouldBe 1
            dao.findPlaylistById(playlistId)?.id shouldBe playlistId
        }

        @Test
        fun videoIsAlreadyAdded_videoIsNotUpdated() = rule.runWithScope {
            // setup
            dao.addChannelEntities(channels)
            val video = YouTubeVideoEntity.upcomingStream(videoId.value, channel = channel)
            extendedSource.addVideo(listOf(video))
            // exercise
            extendedSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(playlistId).size shouldBe 1
            dao.findPlaylistById(playlistId)?.id shouldBe playlistId
            dao.findVideosById(setOf(videoId)).asClue {
                it.size shouldBe 1
                it.first().item.id shouldBe videoId
                it.first().item.liveBroadcastContent shouldBe YouTubeVideo.BroadcastType.UPCOMING
            }
        }

        @Test
        fun itemWasReplaced_returnsItems() = rule.runWithScope {
            // setup
            dao.addChannelEntities(channels)
            extendedSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            // exercise
            val u = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = listOf(
                    FakeYouTubeClient.playlistItemDetail(
                        id = YouTubePlaylistItem.Id("playlist_1"),
                        playlistId = playlistId,
                        channel = channelTable(),
                    ),
                ).toUpdatable(Instant.EPOCH),
            )
            extendedSource.updatePlaylistWithItems(u.item, u.cacheControl)
            // verify
            dao.findPlaylistItemByPlaylistId(playlistId).size shouldBe 1
            dao.findPlaylistById(playlistId)?.id shouldBe playlistId
        }
    }

    class SimpleFetchPlaylistWithItems : Base() {
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
            empty to emptyList(),
        ).map { (playlistId, items) ->
            playlistId to YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId),
                items = items.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
        }.toMap()

        @Before
        fun setup() = rule.runWithScope {
            items.values.forEach { extendedSource.updatePlaylistWithItems(it.item, it.cacheControl) }
        }

        @Test
        fun simple_returns1Item() = rule.runWithScope {
            // exercise
            val actual = localSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 1
        }

        @Test
        fun simple_addSameItem_returns1Item() = rule.runWithScope {
            // setup
            val updatable = localSource.fetchPlaylistWithItems(simple, 10).map {
                it?.item?.update(items[simple]!!.item.items.toUpdatable(fetchedAt = Instant.EPOCH))
            }.getOrNull()
            extendedSource.updatePlaylistWithItems(updatable!!.item, updatable.cacheControl)
            // exercise
            val actual = localSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 1
        }

        @Test
        fun simple_addNewItems_returns2Items() = rule.runWithScope {
            // setup
            val newItems = listOf(
                FakeYouTubeClient.playlistItemDetail(
                    id = YouTubePlaylistItem.Id("item2"),
                    playlistId = simple,
                    videoId = YouTubeVideo.Id("video_item2"),
                    channel = channelTable(),
                ),
            ) + checkNotNull(items[simple]!!.item.items)
            val updatable = localSource.fetchPlaylistWithItems(simple, 10).map {
                it?.item?.update(newItems.toUpdatable(fetchedAt = Instant.EPOCH))
            }.getOrNull()
            extendedSource.updatePlaylistWithItems(updatable!!.item, updatable.cacheControl)
            // exercise
            val actual = localSource.fetchPlaylistWithItems(simple, 10).getOrNull()
            // verify
            actual?.item?.items?.size shouldBe 2
        }

        @Test
        fun private_returnsEmpty() = rule.runWithScope {
            // exercise
            val actual = localSource.fetchPlaylistWithItems(privatePlaylist, 10).getOrNull()
            // verify
            actual?.item?.items.shouldBeEmpty()
        }

        @Test
        fun empty_returnsEmpty() = rule.runWithScope {
            // exercise
            val actual = localSource.fetchPlaylistWithItems(empty, 10).getOrNull()
            // verify
            actual?.item?.items.shouldBeEmpty()
        }
    }

    class CleanUp : Base() {
        private val channel = channelTable()
        private val upcoming = YouTubeVideoEntity.upcomingStream(channel = channel)
        private val live = YouTubeVideoEntity.liveStreaming(channel = channel)
        private val archivedInPlaylist = YouTubeVideoEntity.archivedStream(channel = channel)
        private val videosInPlaylist = listOf(upcoming, live, archivedInPlaylist)
        private val freeChat = YouTubeVideoEntity.freeChat()
        private val unusedArchive = YouTubeVideoEntity.archivedStream(id = "unused_archive")
        private val endlessLive = YouTubeVideoEntity.liveStreaming(id = "endless_live")
        private val videos = videosInPlaylist + listOf(unusedArchive, freeChat, endlessLive)

        @Before
        fun setup() = rule.runWithScope {
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
            extendedSource.updatePlaylistWithItems(updatable.item, updatable.cacheControl)
            val channels = videos.map { it.item.channel }.distinctBy { it.id }.toList()
            extendedSource.addPagedSubscription(
                channels.map {
                    FakeYouTubeClient.subscription("subs_${it.id.value}", it)
                },
            )
            dao.addChannelEntities(channels)
            localSource.addChannelRelatedPlaylists(
                listOf(
                    object : YouTubeChannelRelatedPlaylist {
                        override val id: YouTubeChannel.Id get() = channel.id
                        override val uploadedPlayList: YouTubePlaylist.Id? get() = playlistId
                    },
                ),
            )
            extendedSource.addVideo(videos)
        }

        @Test
        fun remainsUnfinishedVideos() = rule.runWithScope {
            // exercise
            extendedSource.cleanUp()
            // verify
            database.findAllArchivedVideos().shouldContainExactlyInAnyOrder(archivedInPlaylist.item.id)
            dao.findUnusedVideoIds().shouldBeEmpty()
            val actual = dao.findVideosById(videos.map { it.item.id })
            actual.containsVideoIdInAnyOrder(upcoming, live, freeChat, endlessLive)
        }

        @Test
        fun liveIsUpdatedAsArchived_remainsUpcomingAndFreeChat() = rule.runWithScope {
            // setup
            val duration = Duration.ofHours(1)
            val finishedLive = live.item.liveFinished(duration)
            extendedSource.addVideo(listOf(finishedLive))
            // exercise
            extendedSource.cleanUp()
            // verify
            database.findAllArchivedVideos()
                .shouldContainExactlyInAnyOrder(listOf(live, archivedInPlaylist).map { it.item.id })
            dao.findUnusedVideoIds().shouldBeEmpty()
            val actual = dao.findVideosById(videos.map { it.item.id })
            actual.containsVideoIdInAnyOrder(upcoming, freeChat, endlessLive)
        }
    }

    class WhenSubscriptionIsRemoved : Base() {
        private val allDomainChannels = (1..3).map { FakeYouTubeClient.channelDetail(it) }
        private val subscriptions = allDomainChannels.mapIndexed { i, ch ->
            object : YouTubeSubscriptionRelevanceOrdered,
                YouTubeSubscription by FakeYouTubeClient.subscription("sub_${ch.id.value}}", ch) {
                override val order: Int get() = i
            }
        }

        @Before
        fun setup() = rule.runWithScope {
            extendedSource.addPagedSubscription(subscriptions)

            val relatedPlaylists = allDomainChannels.map { ch ->
                val uploadPlaylistId = YouTubePlaylist.Id("pl_${ch.id.value}")
                object : YouTubeChannelRelatedPlaylist {
                    override val uploadedPlayList: YouTubePlaylist.Id? get() = uploadPlaylistId
                    override val id: YouTubeChannel.Id get() = ch.id
                }
            }
            localSource.addChannelRelatedPlaylists(relatedPlaylists)

            allDomainChannels.forEach { domainChannel ->
                val videosForThisChannel = (1..3).map {
                    YouTubeVideoEntity.uploadedVideo(
                        id = "vid_${domainChannel.id.value}_$it",
                        channel = domainChannel,
                    )
                }
                val uploadPlaylistId = YouTubePlaylist.Id("pl_${domainChannel.id.value}")
                val playlistItems = videosForThisChannel.mapIndexed { videoIndex, ytVideo ->
                    FakeYouTubeClient.playlistItemDetail(
                        id = YouTubePlaylistItem.Id("pi_${uploadPlaylistId.value}_${ytVideo.item.id.value}"),
                        playlistId = uploadPlaylistId,
                        channel = domainChannel,
                        videoId = ytVideo.item.id,
                    )
                }
                val playlistWithItems = YouTubePlaylistWithItem.newPlaylist(
                    playlist = playlist(uploadPlaylistId),
                    items = playlistItems.toUpdatable(),
                )

                extendedSource.updatePlaylistWithItems(playlistWithItems.item, playlistWithItems.cacheControl)
                extendedSource.addVideo(videosForThisChannel)
            }
        }

        @Test
        fun init() = rule.runWithScope {
            val subs = dao.fetchAllSubscriptionIds()
            val summaries = dao.findSubscriptionSummaries(subs)
            assertSoftly {
                summaries shouldHaveSize 3
                dao.findChannels(summaries.map { it.channelId }.toSet()) shouldHaveSize 3
                summaries.forAll { s ->
                    s.uploadedPlaylistId shouldNotBeNull {
                        dao.findPlaylistWithItemIds(this) shouldNotBeNull {
                            items shouldHaveSize 3
                            dao.findVideosById(items.map { it.videoId }) shouldHaveSize 3
                        }
                    }
                }
                dao.findAllSubscriptions() shouldHaveSize 3
            }
        }

        private val newSubs = subscriptions.take(2).map { it.id }
        private val removed = (subscriptions.map { it.id } - newSubs)

        @Test
        fun cleanUpByRemainingSubscriptionIds() = rule.runWithScope {
            // setup
            val removedSummary = dao.findSubscriptionSummaries(removed).first()
            val uploadedPlaylistId = removedSummary.uploadedPlaylistId!!
            val items = dao.findPlaylistItemByPlaylistId(uploadedPlaylistId).shouldHaveSize(3)
            // exercise
            extendedSource.syncSubscriptionList(
                newSubs.toSet(),
                listOf(YouTubeSubscriptionQuery.forAlphabetical(0, null, "valid_etag")),
            )
            extendedSource.cleanUp()
            // verify
            dao.check(removedSummary, items)
        }

        @Test
        fun hasLogs() = rule.runWithScope {
            // setup
            val removedSummary = dao.findSubscriptionSummaries(removed).first()
            val uploadedPlaylistId = removedSummary.uploadedPlaylistId!!
            val items = dao.findPlaylistItemByPlaylistId(uploadedPlaylistId).shouldHaveSize(3)
            val videoId = dao.findVideoIdsByChannelId(setOf(removedSummary.channelId))
            localSource.addChannelLogs(
                listOf(
                    object : YouTubeChannelLog {
                        override val id: YouTubeChannelLog.Id get() = YouTubeChannelLog.Id("log1")
                        override val dateTime: Instant get() = Instant.EPOCH
                        override val videoId: YouTubeVideo.Id? get() = videoId.first()
                        override val channelId: YouTubeChannel.Id get() = removedSummary.channelId
                        override val thumbnailUrl: String get() = ""
                        override val title: String get() = ""
                        override val type: String get() = ""
                    },
                ),
            )
            // exercise
            extendedSource.syncSubscriptionList(
                newSubs.toSet(),
                listOf(YouTubeSubscriptionQuery.forAlphabetical(0, null, "valid_etag")),
            )
            extendedSource.cleanUp()
            // verify
            dao.findChannelLogs(removedSummary.channelId).shouldBeEmpty()
            dao.check(removedSummary, items)
            database.findAllArchivedVideos() shouldHaveSize 6
        }

        @Test
        fun hasVideoNotAsPlaylistItem() = rule.runWithScope {
            // setup
            val removedSummary = dao.findSubscriptionSummaries(removed).first()
            val uploadedPlaylistId = removedSummary.uploadedPlaylistId!!
            val items = dao.findPlaylistItemByPlaylistId(uploadedPlaylistId).shouldHaveSize(3)
            extendedSource.addVideo(listOf(YouTubeVideoEntity.liveStreaming(channel = allDomainChannels.last())))
            // exercise
            extendedSource.syncSubscriptionList(
                newSubs.toSet(),
                listOf(YouTubeSubscriptionQuery.forAlphabetical(0, null, "valid_etag")),
            )
            extendedSource.cleanUp()
            // verify
            dao.check(removedSummary, items)
        }

        private suspend fun YouTubeDao.check(
            removedSummary: YouTubeSubscriptionSummary,
            items: List<YouTubePlaylistItemDetail>,
        ) {
            val removed = setOf(removedSummary.subscriptionId)
            val uploadedPlaylistId = removedSummary.uploadedPlaylistId!!
            fetchAllSubscriptionIds().shouldContainExactly(newSubs)
            findSubscriptionSummaries(removed).shouldBeEmpty()
            findPlaylistWithItemIds(uploadedPlaylistId).shouldBeNull()
            findPlaylistItemByPlaylistId(uploadedPlaylistId).shouldBeEmpty()
            val channelIds = setOf(removedSummary.channelId)
            findChannelRelatedPlaylists(channelIds).shouldBeEmpty()
            findChannelDetail(channelIds).shouldBeEmpty()
            findChannels(channelIds).shouldBeEmpty()
            findVideosById(items.map { it.videoId }).shouldBeEmpty()
            findSubscriptionQuery(0).shouldNotBeNull()
                .eTag shouldBe "valid_etag"
        }
    }

    abstract class Base {
        @get:Rule
        internal val rule = YouTubeDatabaseTestRule()
    }
}

fun List<Updatable<YouTubeVideoExtended>>.containsVideoIdInAnyOrderElementsOf(
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

internal data class YouTubeVideoEntity(
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
    override val liveBroadcastContent: YouTubeVideo.BroadcastType,
) : YouTubeVideo {

    companion object {
        fun uploadedVideo(
            id: String = "uploaded_video",
            fetchedAt: Instant = Instant.EPOCH,
            channel: YouTubeChannel = channelTable(),
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
            channel = channel,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun archivedStream(
            id: String = "archived_stream",
            scheduledStartDateTime: Instant = Instant.ofEpochMilli(20),
            actualStartDateTime: Instant = scheduledStartDateTime,
            actualEndDateTime: Instant = Instant.ofEpochSecond(10 * 60),
            channel: YouTubeChannel = channelTable(),
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            channel = channel,
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            actualEndDateTime = actualEndDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun liveStreaming(
            id: String = "live_streaming",
            title: String = "title",
            channel: YouTubeChannel = channelTable(),
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(1000),
            actualStartDateTime: Instant = scheduledStartDateTime,
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = title,
            channel = channel,
            scheduledStartDateTime = scheduledStartDateTime,
            actualStartDateTime = actualStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
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
            title: String = "",
            scheduledStartDateTime: Instant = Instant.ofEpochSecond(5000),
            fetchedAt: Instant = Instant.EPOCH,
            channel: YouTubeChannel = channelTable(),
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            channel = channel,
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            title = title,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun unscheduledUpcoming(
            id: String = "unscheduled_upcoming",
            channel: YouTubeChannel = channelTable(),
            fetchedAt: Instant = Instant.EPOCH,
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            channel = channel,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
        ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(fetchedAt))
            .extend(old = null, isFreeChat = false)

        fun freeChat(
            id: String = "free_chat",
            title: String = "free chat",
            scheduledStartDateTime: Instant = Instant.EPOCH + Duration.ofDays(30),
            fetchedAt: Instant = Instant.EPOCH,
            channel: YouTubeChannel = channelTable(),
        ): Updatable<YouTubeVideoExtended> = YouTubeVideoEntity(
            id = YouTubeVideo.Id(id),
            title = title,
            channel = channel,
            scheduledStartDateTime = scheduledStartDateTime,
            liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
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
