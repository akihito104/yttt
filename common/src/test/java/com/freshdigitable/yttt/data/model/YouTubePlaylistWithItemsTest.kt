package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.MAX_AGE_MAX
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.update
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.fromRemote
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

@RunWith(Enclosed::class)
class YouTubePlaylistWithItemsTest {
    class Init {
        private val playlistId = YouTubePlaylist.Id("playlist")

        @Test
        fun create_newItemIsNull_returnsMaxDuration() {
            // setup
            // exercise
            val actual = YouTubePlaylistWithItem.newPlaylist<YouTubePlaylistItem>(
                playlist = playlist(playlistId, Instant.EPOCH),
                items = Updatable.create(null, CacheControl.fromRemote(Instant.EPOCH)),
            )
            // verify
            assertThat(actual.cacheControl.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(actual.item.addedItems).isEmpty()
        }

        @Test
        fun create_newItemIsEmpty_returnsMaxDuration() {
            // setup
            // exercise
            val actual = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId, Instant.EPOCH),
                items = emptyList<YouTubePlaylistItemDetail>()
                    .toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            // verify
            assertThat(actual.cacheControl.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(actual.item.addedItems).isEmpty()
        }

        @Test
        fun create_newItemIsNotEmpty_returnsDefaultDuration() {
            // setup
            val playlistItem = playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_01"),
            )
            // exercise
            val actual = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId, Instant.EPOCH),
                items = listOf(playlistItem).toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            // verify
            assertThat(actual.cacheControl.maxAge).isEqualTo(MAX_AGE_DEFAULT)
            assertThat(actual.item.addedItems.map { it.videoId })
                .containsExactlyInAnyOrder(playlistItem.videoId)
        }

        @Test
        fun update_playlistItemsIsModified_returnsDefaultDuration() {
            // setup
            val playlistWithItems = playlistWithItems(
                playlistId = playlistId,
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
            )
            val targetPlaylistItem = playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_02"),
            )
            // exercise
            val actual = playlistWithItems.update(
                listOf(
                    targetPlaylistItem,
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ).toUpdatable(),
            )
            // verify
            assertThat(actual.cacheControl.maxAge).isEqualTo(MAX_AGE_DEFAULT)
            assertThat(actual.item.addedItems.map { it.videoId })
                .containsExactlyInAnyOrder(targetPlaylistItem.videoId)
        }

        @Test
        fun update_playlistItemsIsModifiedWithEmpty_returnsMaxDuration() {
            // setup
            val playlistWithItems = playlistWithItems(
                playlistId = playlistId,
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
            )
            // exercise
            val actual =
                playlistWithItems.update(emptyList<YouTubePlaylistItemDetail>().toUpdatable())
            // verify
            assertThat(actual.cacheControl.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(actual.item.addedItems.map { it.videoId }).isEmpty()
        }
    }

    class PlaylistIsNotModified {
        private val playlistId = YouTubePlaylist.Id("playlist")
        private val latestModified = Instant.ofEpochMilli(20000)
        private val items = listOf(
            playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_02"),
                publishedAt = latestModified,
            ),
            playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_01"),
                publishedAt = Instant.ofEpochMilli(15000),
            ),
        )

        private fun playlistWithItems(): YouTubePlaylistWithItemDetails =
            playlistWithItems(playlistId, items)

        @Test
        fun within24HourOfLatestPublishedDatetime_maxAgeIsNotChanged() {
            // setup
            // exercise
            val sut = playlistWithItems().update(
                items.toUpdatable(
                    fetchedAt = latestModified + Duration.ofDays(1).minusMillis(1)
                ),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(MAX_AGE_DEFAULT)
        }

        @Test
        fun after24HourOfLatestPublishedDatetime_maxAgeIsMultipliedBy2() {
            // setup
            // exercise
            val sut = playlistWithItems().update(
                items.toUpdatable(fetchedAt = latestModified + Duration.ofDays(1)),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(MAX_AGE_DEFAULT.multipliedBy(2))
        }

        @Test
        fun after7Days_maxAgeIsMaxDuration() {
            // setup
            // exercise
            val sut = playlistWithItems().update(
                items.toUpdatable(fetchedAt = latestModified + Duration.ofDays(7)),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun upperLimitOfMaxAgeIsMaxDuration() {
            // setup
            // exercise
            val sut = playlistWithItems().update(
                items.toUpdatable(fetchedAt = latestModified + Duration.ofDays(30)),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun updatedLatestPublishedAt_basedOnLatestPublishedAt_maxAgeBy4() {
            // setup
            val updatedLatest = latestModified + Duration.ofHours(3)
            val newItems = items.associateBy { it.id }.toMutableMap().apply {
                val latest = items.maxBy { it.publishedAt }
                this[latest.id] = playlistItem(
                    playlistId = latest.playlistId,
                    itemId = latest.id,
                    publishedAt = updatedLatest,
                )
            }.values
            // exercise
            val sut = playlistWithItems().update(
                newItems.toList()
                    .toUpdatable(fetchedAt = updatedLatest + Duration.ofDays(3).minusMillis(1)),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(
                MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = 2).toLong())
            )
        }

        @Test
        fun updatedLatestPublishedAt_basedOnLatestPublishedAt_maxAgeBy8() {
            // setup
            val updatedLatest = latestModified + Duration.ofHours(3)
            val newItems = items.associateBy { it.id }.toMutableMap().apply {
                val latest = items.maxBy { it.publishedAt }
                this[latest.id] = playlistItem(
                    playlistId = latest.playlistId,
                    itemId = latest.id,
                    publishedAt = updatedLatest,
                )
            }.values
            // exercise
            val sut = playlistWithItems().update(
                newItems.toList().toUpdatable(fetchedAt = updatedLatest + Duration.ofDays(3)),
            )
            // verify
            assertThat(sut.cacheControl.maxAge).isEqualTo(
                MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = 3).toLong())
            )
        }
    }
}

private fun playlist(
    playlistId: YouTubePlaylist.Id,
    fetchedAt: Instant = Instant.EPOCH,
): Updatable<YouTubePlaylist> = FakeYouTubeClient.playlist(playlistId)
    .toUpdatable(CacheControl.fromRemote(fetchedAt))

private fun playlistWithItems(
    playlistId: YouTubePlaylist.Id,
    items: List<YouTubePlaylistItemDetail>,
): YouTubePlaylistWithItemDetails = object : YouTubePlaylistWithItemDetails {
    override val playlist: YouTubePlaylist get() = FakeYouTubeClient.playlist(playlistId)
    override val items: List<YouTubePlaylistItemDetail> get() = items
    override val addedItems: List<YouTubePlaylistItemDetail> get() = emptyList()
}

private fun playlistItem(
    playlistId: YouTubePlaylist.Id,
    itemId: YouTubePlaylistItem.Id,
    publishedAt: Instant = Instant.EPOCH,
): YouTubePlaylistItemDetail = FakeYouTubeClient.playlistItem(
    playlistId = playlistId,
    id = itemId,
    publishedAt = publishedAt,
)
