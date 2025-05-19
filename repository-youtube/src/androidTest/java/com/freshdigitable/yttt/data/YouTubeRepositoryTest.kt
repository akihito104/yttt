package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.data.source.remote.YouTubeVideoRemote
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
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
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidTest
class YouTubeRepositoryTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sut: YouTubeRepository

    @Test
    fun init() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        FakeRemoteSourceModule.videoList = {
            listOf(YouTubeVideoRemote(Video().apply {
                id = "1"
                snippet = VideoSnippet().apply {
                    channelId = "channel_1"
                    channelTitle = "channel_1_title"
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
            }))
        }
        FakeRemoteSourceModule.channelList = {
            listOf(object : YouTubeChannelDetail {
                override val iconUrl: String get() = "<url is here>"
                override val id: YouTubeChannel.Id get() = YouTubeChannel.Id("channel_1")
                override val title: String get() = "channel_1_title"
                override val bannerUrl: String? get() = "<url is here>"
                override val subscriberCount: BigInteger get() = BigInteger.TEN
                override val isSubscriberHidden: Boolean get() = false
                override val videoCount: BigInteger get() = BigInteger.TEN
                override val viewsCount: BigInteger get() = BigInteger.TEN
                override val publishedAt: Instant get() = Instant.EPOCH
                override val customUrl: String get() = "channel_1"
                override val keywords: Collection<String> get() = emptyList()
                override val description: String? get() = "description"
                override val uploadedPlayList: YouTubePlaylist.Id? get() = YouTubePlaylist.Id("channel_1_uploaded")
            })
        }
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

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [YouTubeModule::class],
)
interface FakeRemoteSourceModule {
    companion object {
        var videoList: ((Set<YouTubeVideo.Id>) -> List<YouTubeVideo>)? = null
        var channelList: ((Set<YouTubeChannel.Id>) -> List<YouTubeChannelDetail>)? = null

        @Provides
        @Singleton
        fun provideYouTubeClient(): YouTubeClient {
            return object : YouTubeClient {
                override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> =
                    NetworkResponse.create(videoList!!.invoke(ids))

                override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> =
                    NetworkResponse.create(channelList!!.invoke(ids))

                override fun fetchSubscription(
                    pageSize: Long,
                    offset: Int,
                    token: String?
                ): NetworkResponse<List<YouTubeSubscription>> {
                    TODO("Not yet implemented")
                }

                override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> {
                    TODO("Not yet implemented")
                }

                override fun fetchPlaylistItems(
                    id: YouTubePlaylist.Id,
                    maxResult: Long
                ): NetworkResponse<List<YouTubePlaylistItem>> {
                    TODO("Not yet implemented")
                }

                override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> {
                    TODO("Not yet implemented")
                }

                override fun fetchLiveChannelLogs(
                    channelId: YouTubeChannel.Id,
                    publishedAfter: Instant?,
                    maxResult: Long?,
                    token: String?
                ): NetworkResponse<List<YouTubeChannelLog>> {
                    TODO("Not yet implemented")
                }
            }
        }
    }
}

interface FakeDbModule : InMemoryDbModule
interface FakeCoroutineScopeModule : TestCoroutineScopeModule
interface FakeClockModule : FakeDateTimeProviderModule
