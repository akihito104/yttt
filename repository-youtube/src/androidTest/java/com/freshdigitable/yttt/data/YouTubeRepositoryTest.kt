package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.RecordableFunction.Companion.spy
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeVideoRemote
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.google.api.client.util.DateTime
import com.google.api.services.youtube.model.Thumbnail
import com.google.api.services.youtube.model.ThumbnailDetails
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoLiveStreamingDetails
import com.google.api.services.youtube.model.VideoSnippet
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class YouTubeRepositoryTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sut: YouTubeRepository

    @After
    fun tearDown() {
        FakeYouTubeClientModule.clean()
        RecordableFunction.recorded.run {
            forEach { assertThat(it.actual).isEqualTo(it.expected) }
            clear()
        }
    }

    @Test
    fun fetchVideoList_itemFromRemoteHasIconUrl() = runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = spy(expected = 1) { listOf(video(1, channelDetail)) },
            channelList = spy(expected = 1) { listOf(channelDetail) },
        )
        TestCoroutineScopeModule.testScheduler = testScheduler
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
    fun fetchVideoList_itemFromCacheHasIconUrl() = runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(999)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        localSource.addChannelList(listOf(channelDetail))
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
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
    fun fetchVideoList_updatedItemHasIconUrl() = runTest {
        // setup
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(1000)
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeYouTubeClientModule.client = FakeRemoteSource(
            videoList = spy(expected = 1) { listOf(video) },
        )
        hiltRule.inject()
        localSource.addChannelList(listOf(channelDetail))
        sut.addVideo(listOf(object : YouTubeVideoExtended, YouTubeVideo by video {
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
    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> =
        NetworkResponse.create(videoList!!.invoke(ids))

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
        NetworkResponse.create(channelList!!.invoke(ids))
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDbModule : InMemoryDbModule
interface FakeCoroutineScopeModule : TestCoroutineScopeModule
interface FakeClockModule : FakeDateTimeProviderModule

interface RecordableFunction<T, R> : (T) -> R {
    val actual: Int
    val expected: Int

    companion object {
        val recorded = mutableListOf<RecordableFunction<*, *>>()

        fun <T, R> spy(expected: Int, body: (T) -> R): RecordableFunction<T, R> =
            object : RecordableFunction<T, R> {
                private var _count: Int = 0
                override val actual: Int get() = _count
                override val expected: Int get() = expected
                override fun invoke(p1: T): R {
                    _count++
                    return body(p1)
                }
            }.also { recorded.add(it) }
    }
}
