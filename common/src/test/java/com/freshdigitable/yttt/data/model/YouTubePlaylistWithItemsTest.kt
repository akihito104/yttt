package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Noted.Companion.noted
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.MAX_AGE_MAX
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem.Companion.update
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

class YouTubePlaylistWithItemsTest : ShouldSpec({
    context("fundamental") {
        val playlistId = YouTubePlaylist.Id("playlist")

        should("create_newItemIsEmpty_returnsMaxDuration") {
            // setup
            // exercise
            val actual = YouTubePlaylistWithItem.newPlaylist(
                playlist = playlist(playlistId, Instant.EPOCH),
                items = emptyList<YouTubePlaylistItemDetail>()
                    .toUpdatable(CacheControl.fromRemote(Instant.EPOCH)),
            )
            // verify
            actual.cacheControl.maxAge shouldBe MAX_AGE_MAX
            actual.item.addedItems.shouldBeEmpty()
        }

        should("create_newItemIsNotEmpty_returnsDefaultDuration") {
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
            actual.cacheControl.maxAge shouldBe MAX_AGE_DEFAULT
            actual.item.addedItems.map { it.videoId }
                .shouldContainExactlyInAnyOrder(playlistItem.videoId)
        }

        should("update_playlistItemsIsModified_returnsDefaultDuration") {
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
            actual.cacheControl.maxAge shouldBe MAX_AGE_DEFAULT
            actual.item.addedItems.map { it.videoId }
                .shouldContainExactlyInAnyOrder(targetPlaylistItem.videoId)
        }

        should("update_playlistItemsIsModifiedWithEmpty_returnsMaxDuration") {
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
            actual.cacheControl.maxAge shouldBe MAX_AGE_MAX
            actual.item.addedItems.map { it.videoId }.shouldBeEmpty()
        }
    }

    context("maxAge of playlist") {
        val playlistId = YouTubePlaylist.Id("playlist")
        val latestModified = Instant.ofEpochMilli(20000)
        val items = listOf(
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
        val currentPlaylistWithItems = playlistWithItems(playlistId, items)

        data class TestData(val duration: Noted<Duration>, val expected: Noted<Duration>)
        withData(
            nameFn = { "${it.duration.note} from latest item: maxAge=${it.expected.note}" },
            TestData(
                Duration.ofDays(1).minusMillis(1).noted("within a day"),
                MAX_AGE_DEFAULT.noted("default"),
            ),
            TestData(
                Duration.ofDays(1).noted("after a day"),
                MAX_AGE_DEFAULT.multipliedBy(2).noted("default * 2"),
            ),
            TestData(Duration.ofDays(7).noted("after a week"), MAX_AGE_MAX.noted("max duration")),
            TestData(Duration.ofDays(30).noted("after 30 days"), MAX_AGE_MAX.noted("max duration")),
        ) { (duration, expected) ->
            // setup
            val newItems = items.toUpdatable(fetchedAt = latestModified + duration.value)
            // exercise
            val sut = currentPlaylistWithItems.update(newItems)
            // verify
            sut.cacheControl.maxAge shouldBe expected.value
        }

        context("updated latest item after 3 hours") {
            val updatedLatest = latestModified + Duration.ofHours(3)
            val newItems = items.associateBy { it.id }.toMutableMap().apply {
                val latest = items.maxBy { it.publishedAt }
                this[latest.id] = playlistItem(
                    playlistId = latest.playlistId,
                    itemId = latest.id,
                    publishedAt = updatedLatest,
                )
            }.values.toList()
            withData(
                nameFn = { "maxAge=${it.duration.note}" },
                (1L..7L).map {
                    listOf(
                        TestData(
                            Duration.ofDays(it).minusMillis(it).noted("within $it day"),
                            MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = it.toInt() - 1).toLong())
                                .noted("default * $it"),
                        ),
                        TestData(
                            Duration.ofDays(it).noted("after $it day"),
                            MAX_AGE_DEFAULT.multipliedBy(2.0.pow(n = it.toInt()).toLong())
                                .noted("default * $it"),
                        )
                    )
                }.flatten()
            ) { (duration, expected) ->
                val updatable = newItems.toUpdatable(fetchedAt = updatedLatest + duration.value)
                // exercise
                val sut = currentPlaylistWithItems.update(updatable)
                // verify
                sut.cacheControl.maxAge shouldBe expected.value
            }
        }
    }
})

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
): YouTubePlaylistItemDetail = FakeYouTubeClient.playlistItemDetail(
    playlistId = playlistId,
    id = itemId,
    publishedAt = publishedAt,
)

class Noted<T>(val value: T, val note: String) {
    companion object {
        fun <T> T.noted(note: String) = Noted(this, note)
    }
}
