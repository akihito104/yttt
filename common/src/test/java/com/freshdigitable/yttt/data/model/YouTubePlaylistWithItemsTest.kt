package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.MAX_AGE_MAX
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
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
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = null,
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(sut.addedItems).isEmpty()
        }

        @Test
        fun create_newItemIsEmpty_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = emptyList(),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(sut.addedItems).isEmpty()
        }

        @Test
        fun create_newItemIsNotEmpty_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01"),
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT)
            assertThat(sut.addedItems.map { it.id })
                .containsExactlyInAnyOrder(YouTubePlaylistItem.Id("item_id_01"))
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
            val sut = playlistWithItems.update(
                newItems = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_02"),
                    ),
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT)
            assertThat(sut.addedItems.map { it.id })
                .containsExactlyInAnyOrder(YouTubePlaylistItem.Id("item_id_02"))
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
            val sut = playlistWithItems.update(
                newItems = emptyList(),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
            assertThat(sut.addedItems).isEmpty()
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

        private fun playlistWithItems(
            maxAge: Duration,
        ): YouTubePlaylistWithItems = playlistWithItems(playlistId, maxAge, items)

        @Test
        fun within24HourOfLatestPublishedDatetime_maxAgeIsNotChanged() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
            // exercise
            val sut = playlistWithItems(maxAge).update(
                newItems = items,
                fetchedAt = latestModified + Duration.ofDays(1).minusMillis(1),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT)
        }

        @Test
        fun after24HourOfLatestPublishedDatetime_maxAgeIsMultipliedBy2() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
            // exercise
            val sut = playlistWithItems(maxAge).update(
                newItems = items,
                fetchedAt = latestModified + Duration.ofDays(1),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT.multipliedBy(2))
        }

        @Test
        fun after7Days_maxAgeIsMaxDuration() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
            // exercise
            val sut = playlistWithItems(maxAge).update(
                newItems = items,
                fetchedAt = latestModified + Duration.ofDays(7),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun upperLimitOfMaxAgeIsMaxDuration() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
            // exercise
            val sut = playlistWithItems(maxAge).update(
                newItems = items,
                fetchedAt = latestModified + Duration.ofDays(30),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun updatedLatestPublishedAt_basedOnLatestPublishedAt_maxAgeBy4() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
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
            val sut = playlistWithItems(maxAge).update(
                newItems = newItems.toList(),
                fetchedAt = updatedLatest + Duration.ofDays(3).minusMillis(1),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = 2).toLong()))
        }

        @Test
        fun updatedLatestPublishedAt_basedOnLatestPublishedAt_maxAgeBy8() {
            // setup
            val maxAge = MAX_AGE_DEFAULT
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
            val sut = playlistWithItems(maxAge).update(
                newItems = newItems.toList(),
                fetchedAt = updatedLatest + Duration.ofDays(3),
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = 3).toLong()))
        }
    }
}

private fun playlist(playlistId: YouTubePlaylist.Id): YouTubePlaylist = object : YouTubePlaylist {
    override val id: YouTubePlaylist.Id = playlistId
    override val title: String = ""
    override val thumbnailUrl: String = ""
}

private fun playlistWithItems(
    playlistId: YouTubePlaylist.Id,
    maxAge: Duration = Duration.ZERO,
    items: List<YouTubePlaylistItem>,
    fetchedAt: Instant = Instant.EPOCH,
): YouTubePlaylistWithItems = YouTubePlaylistWithItems.create(
    playlist = object : YouTubePlaylistUpdatable, YouTubePlaylist by playlist(playlistId) {
        override val maxAge: Duration = maxAge
        override val fetchedAt: Instant = fetchedAt
    },
    items = items,
)

private fun playlistItem(
    playlistId: YouTubePlaylist.Id,
    itemId: YouTubePlaylistItem.Id,
    publishedAt: Instant = Instant.EPOCH,
): YouTubePlaylistItem = YouTubePlaylistItemEntity(
    playlistId = playlistId,
    id = itemId,
    title = "",
    thumbnailUrl = "",
    description = "",
    channel = YouTubeChannelEntity(
        id = YouTubeChannel.Id("channel"),
        title = "channel",
        iconUrl = "",
    ),
    videoId = YouTubeVideo.Id("video"),
    videoOwnerChannelId = null,
    publishedAt = publishedAt,
)

data class YouTubePlaylistItemEntity(
    override val id: YouTubePlaylistItem.Id,
    override val playlistId: YouTubePlaylist.Id,
    override val title: String,
    override val channel: YouTubeChannel,
    override val thumbnailUrl: String,
    override val videoId: YouTubeVideo.Id,
    override val description: String,
    override val videoOwnerChannelId: YouTubeChannel.Id?,
    override val publishedAt: Instant,
) : YouTubePlaylistItem
