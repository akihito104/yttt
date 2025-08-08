package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.data.source.remote.YouTubeVideoRemote
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.CallerVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.internalServerError
import com.freshdigitable.yttt.test.notFound
import com.freshdigitable.yttt.test.notModified
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.model.Thumbnail
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import com.google.api.services.youtube.model.VideoSnippet
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
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

    @Inject
    lateinit var sut: YouTubeRepository

    @After
    fun tearDown() {
        FakeYouTubeClientModule.clean()
    }

    @Test
    fun fetchVideoList_itemFromRemoteHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = recorder.wrap(expected = 1) {
                listOf(video(1, channelDetail)).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
            channelList = recorder.wrap(expected = 1) {
                listOf(channelDetail).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().item.channel.iconUrl).apply {
                isNotNull()
                isNotEmpty()
            }
        }
    }

    @Inject
    lateinit var localSource: YouTubeDataSource.Local

    @Test
    fun fetchVideoList_itemFromCacheHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(999)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)))
        )
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
            override val channel: YouTubeChannel get() = channelDetail
            override val isFreeChat: Boolean get() = false
        }.toUpdatable(Instant.ofEpochMilli(200), Duration.ofMillis(800))))
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().item.channel.iconUrl).apply {
                isNotNull()
                isNotEmpty()
            }
        }
    }

    @Test
    fun fetchVideoList_videoFromRemoteAndChannelFromCache() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(0)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = recorder.wrap(expected = 1) {
                listOf(video(1, channelDetail)).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
        )
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)))
        )
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().item.channel.iconUrl).apply {
                isNotNull()
                isNotEmpty()
            }
        }
    }

    @Test
    fun fetchVideoList_videoFromRemoteAndChannelGetsException_returnsFailure() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(0)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = recorder.wrap(expected = 1) {
                listOf(video(1, channelDetail)).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
            channelList = recorder.wrap(expected = 1) {
                throw YouTubeException.internalServerError()
            },
        )
        hiltRule.inject()
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1) + Duration.ofDays(1)
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isFailure {
            it.isInstanceOf(YouTubeException::class.java)
        }
    }

    @Test
    fun fetchVideoList_updatedItemHasIconUrl() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = recorder.wrap(expected = 1) {
                listOf(video).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
        )
        hiltRule.inject()
        localSource.addChannelDetailList(
            listOf(channelDetail.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)))
        )
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
            override val channel: YouTubeChannel get() = channelDetail
            override val isFreeChat: Boolean get() = false
        }.toUpdatable(Instant.ofEpochMilli(200), Duration.ofMillis(800))))
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().item.channel.iconUrl).apply {
                isNotNull()
                isNotEmpty()
            }
        }
    }

    @Test
    fun fetchPlaylistWithItems() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            playlist = recorder.wrap(expected = 1) { ids ->
                ids.map { FakeYouTubeClient.playlist(it) }
                    .toUpdatable(CacheControl.fromRemote(Instant.ofEpochMilli(1000)))
            },
            playlistItems = recorder.wrap(expected = 1) { ids ->
                {
                    listOf(FakeYouTubeClient.playlistItem(YouTubePlaylistItem.Id("0"), ids))
                        .toUpdatable()
                }
            }
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchPlaylistWithItems(YouTubePlaylist.Id("0"), 10)
        // verify
        assertResultThat(actual).isSuccess {
            assertThat(it!!.item.items).hasSize(1)
        }
    }

    @Test
    fun fetchPlaylistWithItems_receiveNotFoundAtInit_returnsSuccess() = testScope.runTest {
        // setup
        val current = Instant.ofEpochMilli(1000)
        FakeDateTimeProviderModule.instant = current
        FakeYouTubeClientModule.client = FakeRemoteSource(
            playlistItems = recorder.wrap(expected = 1) { ids ->
                val cacheControl = CacheControl.fromRemote(current)
                throw YouTubeException.notFound(cacheControl = cacheControl)
            }
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchPlaylistWithItems(YouTubePlaylist.Id("0"), 10)
        // verify
        assertResultThat(actual).isSuccess {
            assertThat(it!!.item.playlist.id).isEqualTo(YouTubePlaylist.Id("0"))
            assertThat(it.cacheControl.fetchedAt).isEqualTo(current)
        }
    }

    @Test
    fun fetchPlaylistWithItems_receiveNotModified_returnsSuccess() = testScope.runTest {
        // setup
        val current = Instant.ofEpochMilli(1000)
        FakeDateTimeProviderModule.instant = current
        FakeYouTubeClientModule.client = FakeRemoteSource(
            playlistItems = recorder.wrap(expected = 1) { ids ->
                {
                    val cacheControl = CacheControl.fromRemote(current)
                    throw YouTubeException.notModified(cacheControl = cacheControl)
                }
            }
        )
        hiltRule.inject()
        val playlistId = YouTubePlaylist.Id("0")
        val channel = FakeYouTubeClient.channelDetail(1)
        localSource.addChannelDetailList(listOf(channel.toUpdatable()))
        val item = FakeYouTubeClient.playlistItem(YouTubePlaylistItem.Id("0"), playlistId)
        localSource.updatePlaylistWithItems(
            object : YouTubePlaylistWithItems {
                override val playlist: YouTubePlaylist get() = FakeYouTubeClient.playlist(playlistId)
                override val items: List<YouTubePlaylistItem> get() = listOf(item)
                override val eTag: String? get() = "valid_eTag"
            },
            CacheControl.fromRemote(Instant.EPOCH),
        )
        val cache = checkNotNull(localSource.fetchPlaylistWithItems(playlistId, 10).getOrNull())
        assertThat(cache.item.eTag).isEqualTo("valid_eTag")
        // exercise
        val actual = sut.fetchPlaylistWithItems(playlistId, 10)
        // verify
        assertResultThat(actual).isSuccess {
            assertThat(it!!.item.playlist.id).isEqualTo(cache.item.playlist.id)
            assertThat(it.item.items).hasSize(1)
            assertThat(it.item.addedItems).isEmpty()
            assertThat(it.item.eTag).isEqualTo("valid_eTag")
        }
    }
}

private fun video(id: Int, channel: YouTubeChannel): YouTubeVideo = YouTubeVideoRemote(
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

private class FakeRemoteSource(
    val videoList: ((Set<YouTubeVideo.Id>) -> Updatable<List<YouTubeVideo>>)? = null,
    val channelList: ((Set<YouTubeChannel.Id>) -> Updatable<List<YouTubeChannel>>)? = null,
    val playlist: ((Set<YouTubePlaylist.Id>) -> Updatable<List<YouTubePlaylist>>)? = null,
    val playlistItems: ((YouTubePlaylist.Id) -> (String?) -> Updatable<List<YouTubePlaylistItem>>)? = null,
) : FakeYouTubeClient() {
    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        logD { "fetchVideoList: $ids" }
        return NetworkResponse.create(videoList!!.invoke(ids))
    }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannel>> {
        logD { "fetchChannelList: $ids" }
        return NetworkResponse.create(channelList!!.invoke(ids))
    }

    override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> {
        logD { "fetchPlaylist: $ids" }
        return NetworkResponse.create(playlist!!.invoke(ids))
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): NetworkResponse<List<YouTubePlaylistItem>> {
        logD { "fetchPlaylistItems: $id" }
        return NetworkResponse.create(playlistItems!!.invoke(id)(eTag))
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDbModule : InMemoryDbModule
interface FakeCoroutineScopeModule : TestCoroutineScopeModule
interface FakeClockModule : FakeDateTimeProviderModule
