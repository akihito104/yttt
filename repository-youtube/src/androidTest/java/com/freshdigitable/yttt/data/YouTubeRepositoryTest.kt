package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.data.source.remote.YouTubeVideoRemote
import com.freshdigitable.yttt.test.CallerVerifier
import com.freshdigitable.yttt.test.ChannelItemJson
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.PlaylistItemJson
import com.freshdigitable.yttt.test.PlaylistJson
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.VideoJson
import com.freshdigitable.yttt.test.YouTubeErrorJson
import com.freshdigitable.yttt.test.channelDetail
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.playlist
import com.freshdigitable.yttt.test.playlistItem
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.model.Thumbnail
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import com.google.api.services.youtube.model.VideoSnippet
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class YouTubeRepositoryTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule()

    @get:Rule(order = 2)
    val recorder = CallerVerifier()

    @get:Rule(order = 3)
    val server = MockServerRule()

    @Inject
    lateinit var sut: YouTubeRepository

    @Test
    fun fetchVideoList_itemFromRemoteHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        val channel = ChannelItemJson.createSnippet(id = YouTubeChannel.Id("channel_1"))
        server.setClient(
            videoList = recorder.wrap(expected = 1) {
                val video = VideoJson(
                    idNum = 1,
                    channel = channel,
                    scheduledStartDateTime = Instant.parse("2022-01-01T00:00:00Z"),
                )
                listOf(video)
            },
            channelList = recorder.wrap(expected = 1) { (id, _) ->
                check(setOf(channel.id) == id)
                listOf(channel)
            },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        actual.shouldBeSuccess {
            it.first().item.channel.iconUrl.apply {
                shouldNotBeNull()
                shouldNotBeEmpty()
            }
        }
    }

    @Inject
    lateinit var localSource: YouTubeDataSource.Local

    @Inject
    lateinit var extendedSource: YouTubeDataSource.Extended

    @Test
    fun fetchVideoList_itemFromCacheHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(999)
        val channelDetail = channelDetail(1)
        val video = video(1, channelDetail)
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))),
        )
        sut.addVideo(
            listOf(
                object : YouTubeVideoExtended, YouTubeVideo by video {
                    override val channel: YouTubeChannel get() = channelDetail
                    override val isFreeChat: Boolean get() = false
                }.toUpdatable(Instant.ofEpochMilli(200), Duration.ofMillis(800)),
            ),
        )
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        actual.shouldBeSuccess { value ->
            value.first().item.channel.iconUrl.apply {
                shouldNotBeNull()
                shouldNotBeEmpty()
            }
        }
    }

    @Test
    fun fetchVideoList_videoFromRemoteAndChannelFromCache() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(0)
        val channelDetail = channelDetail(1)
        server.setClient(
            videoList = recorder.wrap(expected = 1) {
                val video = VideoJson(1, channelDetail, scheduledStartDateTime = Instant.parse("2022-01-01T00:00:00Z"))
                listOf(video)
            },
        )
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))),
        )
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        actual.shouldBeSuccess { value ->
            value.first().item.channel.iconUrl.apply {
                shouldNotBeNull()
                shouldNotBeEmpty()
            }
        }
    }

    @Test
    fun fetchVideoList_videoFromRemoteAndChannelGetsException_returnsFailure() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(0)
        val channel = YouTubeChannel.Id("channel_1")
        val videoId = YouTubeVideo.Id("${channel.value}_video_1")
        server.setClient(
            videoList = recorder.wrap(expected = 1) {
                listOf(VideoJson(videoId, channel, "title_${channel.value}"))
            },
            channelListRes = recorder.wrap(expected = 1) { YouTubeErrorJson.internalServerError() },
        )
        hiltRule.inject()
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1) + Duration.ofDays(1)
        // exercise
        val actual = sut.fetchVideoList(setOf(videoId))
        // verify
        actual shouldBeFailure {
            it.shouldBeInstanceOf<YouTubeException>()
        }
    }

    @Test
    fun fetchVideoList_updatedItemHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        val channelDetail = channelDetail(1)
        val video = video(1, channelDetail)
        server.setClient(
            videoList = recorder.wrap(expected = 1) { listOf(VideoJson(video)) },
        )
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH))),
        )
        sut.addVideo(
            listOf(
                object : YouTubeVideoExtended, YouTubeVideo by video {
                    override val channel: YouTubeChannel get() = channelDetail
                    override val isFreeChat: Boolean get() = false
                }.toUpdatable(Instant.ofEpochMilli(200), Duration.ofMillis(800)),
            ),
        )
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        actual.shouldBeSuccess { value ->
            value.first().item.channel.iconUrl.apply {
                shouldNotBeNull()
                shouldNotBeEmpty()
            }
        }
    }

    @Test
    fun fetchPlaylistWithItems() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        server.setClient(
            playlist = recorder.wrap(expected = 1) { ids ->
                ids.map { PlaylistJson(it.value) }
            },
            playlistItems = recorder.wrap(expected = 1) { (ids, _) ->
                listOf(PlaylistItemJson("0", ids.value))
            },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchPlaylistWithItems(YouTubePlaylist.Id("0"), 10)
        // verify
        actual shouldBeSuccess {
            it.item.items shouldHaveSize 1
            it.cacheControl.maxAge shouldBe YouTubePlaylistWithItems.MAX_AGE_DEFAULT
        }
    }

    @Test
    fun fetchPlaylistWithItems_receiveNotFoundAtInit_returnsSuccess() = testScope.runTest {
        // setup
        val current = Instant.ofEpochMilli(1000)
        FakeDateTimeProviderModule.instant = current
        server.setClient(
            playlistItemsRes = recorder.wrap(expected = 1) { YouTubeErrorJson.notFound() },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchPlaylistWithItems(YouTubePlaylist.Id("0"), 10)
        // verify
        actual shouldBeSuccess {
            it.item.playlist.id shouldBe YouTubePlaylist.Id("0")
            it.cacheControl.fetchedAt shouldBe current
            it.cacheControl.maxAge shouldBe Duration.ofDays(1)
        }
    }

    @Test
    fun fetchPlaylistWithItems_receiveNotModified_returnsSuccess() = testScope.runTest {
        // setup
        val current = Instant.ofEpochMilli(1000)
        FakeDateTimeProviderModule.instant = current
        server.setClient(
            playlistItemsRes = recorder.wrap(expected = 1) { YouTubeErrorJson.notModified() },
        )
        hiltRule.inject()
        val playlistId = YouTubePlaylist.Id("0")
        val channel = channelDetail(1)
        localSource.addChannelDetailList(listOf(channel.toUpdatable()))
        val item = playlistItem(YouTubePlaylistItem.Id("0"), playlistId)
        extendedSource.updatePlaylistWithItems(
            object : YouTubePlaylistWithItems {
                override val playlist: YouTubePlaylist get() = playlist(playlistId)
                override val items: List<YouTubePlaylistItem> get() = listOf(item)
                override val eTag: String get() = "valid_eTag"
            },
            CacheControl.fromRemote(Instant.EPOCH),
        )
        val cache = checkNotNull(localSource.fetchPlaylistWithItems(playlistId, 10).getOrNull())
        cache.item.eTag shouldBe "valid_eTag"
        // exercise
        val actual = sut.fetchPlaylistWithItems(playlistId, 10)
        // verify
        actual shouldBeSuccess {
            it.item.playlist.id shouldBe cache.item.playlist.id
            it.item.items shouldHaveSize 1
            it.item.addedItems.shouldBeEmpty()
            it.item.eTag shouldBe "valid_eTag"
            it.cacheControl.maxAge shouldBe YouTubePlaylistWithItems.MAX_AGE_DEFAULT
        }
    }
}

private fun video(id: Int, channel: YouTubeChannelTitle): YouTubeVideo = YouTubeVideoRemote(
    Video().apply {
        this.id = "$id"
        snippet = VideoSnippet().apply {
            channelId = channel.id.value
            channelTitle = channel.title
            title = "title$id"
            description = "description"
            liveBroadcastContent = "upcoming"
            thumbnails = ThumbnailDetails().apply {
                standard = Thumbnail().apply {
                    url = "<url is here>"
                }
            }
        }
        liveStreamingDetails = VideoLiveStreamingDetails().apply {
            scheduledStartTime = DateTime("2022-01-01T00:00:00Z")
        }
    },
)

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDbModule : InMemoryDbModule
interface FakeCoroutineScopeModule : TestCoroutineScopeModule
interface FakeClockModule : FakeDateTimeProviderModule
