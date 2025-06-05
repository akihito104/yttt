package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
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
            videoList = recorder.wrap(expected = 1) { listOf(video(1, channelDetail)) },
            channelList = recorder.wrap(expected = 1) { listOf(channelDetail) },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().channel.iconUrl).apply {
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
        localSource.addChannelList(listOf(channelDetail))
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
            override val channel: YouTubeChannel get() = channelDetail
            override val isFreeChat: Boolean get() = false
            override val updatableAt: Instant get() = Instant.ofEpochMilli(1000)
        }))
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().channel.iconUrl).apply {
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
            videoList = recorder.wrap(expected = 1) { listOf(video(1, channelDetail)) },
        )
        hiltRule.inject()
        localSource.addChannelList(listOf(channelDetail))
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().channel.iconUrl).apply {
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
            videoList = recorder.wrap(expected = 1) { listOf(video(1, channelDetail)) },
            channelList = recorder.wrap(expected = 1) {
                throw YouTubeException(500, "Internal error")
            },
        )
        hiltRule.inject()
        localSource.addChannelList(listOf(channelDetail))
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
            videoList = recorder.wrap(expected = 1) { listOf(video) },
        )
        hiltRule.inject()
        localSource.addChannelList(listOf(channelDetail))
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
            override val channel: YouTubeChannel get() = channelDetail
            override val isFreeChat: Boolean get() = false
            override val updatableAt: Instant get() = Instant.ofEpochMilli(1000)
        }))
        // exercise
        val actual = sut.fetchVideoList(setOf(YouTubeVideo.Id("1")))
        // verify
        assertResultThat(actual).isSuccess { value ->
            assertThat(value.first().channel.iconUrl).apply {
                isNotNull()
                isNotEmpty()
            }
        }
    }
}

private fun video(id: Int, channel: YouTubeChannel): YouTubeVideo =
    YouTubeVideoRemote(Video().apply {
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
    })

private class FakeRemoteSource(
    val videoList: ((Set<YouTubeVideo.Id>) -> List<YouTubeVideo>)? = null,
    val channelList: ((Set<YouTubeChannel.Id>) -> List<YouTubeChannelDetail>)? = null,
) : FakeYouTubeClient() {
    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        logD { "fetchVideoList: $ids" }
        return NetworkResponse.create(videoList!!.invoke(ids))
    }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> {
        logD { "fetchChannelList: $ids" }
        return NetworkResponse.create(channelList!!.invoke(ids))
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDbModule : InMemoryDbModule
interface FakeCoroutineScopeModule : TestCoroutineScopeModule
interface FakeClockModule : FakeDateTimeProviderModule
