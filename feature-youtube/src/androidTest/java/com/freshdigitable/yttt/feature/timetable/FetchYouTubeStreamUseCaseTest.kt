package com.freshdigitable.yttt.feature.timetable

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.di.DbModule
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.di.CoroutineModule
import com.freshdigitable.yttt.di.DateTimeModule
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.logD
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class FetchYouTubeStreamUseCaseTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var localSource: YouTubeDataSource.Local

    @Inject
    internal lateinit var sut: FetchYouTubeStreamUseCase

    @Test
    fun earlyReturn_whenNoAccount() = runTest {
        // setup
        FakeYouTubeAccountModule.account = null
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun videoAndSubscriptionAreEmpty_whenSuccess() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.subscription =
            mapOf((0 to null) to (emptyList<YouTubeSubscription>() to null))
        hiltRule.inject()
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun videoFromNewPlaylistItem_returns20Videos() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(10, 2)
        hiltRule.inject()
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        localSource.videos.test {
            assertThat(awaitItem()).hasSize(20)
        }
    }

    @Test
    fun videoFromNewPlaylistItem_with2PagesOfSubscription_returns200Videos() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(100, 2)
        hiltRule.inject()
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        localSource.videos.test {
            assertThat(awaitItem()).hasSize(200)
        }
    }
}

private fun FakeRemoteSourceModule.Companion.setup(subscriptionCount: Int, itemsPerPlaylist: Int) {
    val channelDetail = (1..subscriptionCount).map { channelDetail(it) }
    val videos = (1..(subscriptionCount * itemsPerPlaylist)).map {
        video(it, channelDetail[it % channelDetail.size])
    }
    channel = channelDetail.associate { setOf(it.id) to listOf(it) }
    val chunked = channelDetail.chunked(50)
    subscription = chunked.mapIndexed { i, c ->
        val tokenMatcher = if (i == 0) null else "token$i"
        val nextToken = if (i == chunked.size - 1) null else "token${i + 1}"
        (i * 50 to tokenMatcher) to (c.mapIndexed { j, s -> subscription(j, s) } to nextToken)
    }.toMap()
    video = videos
    playlistItem = channelDetail.associate { c ->
        c.uploadedPlayList!! to videos.filter { it.channel.id == c.id }
            .mapIndexed { i, v -> playlistItem(i, c, v.id) }
    }
}

private fun channelDetail(id: Int): YouTubeChannelDetail = object : YouTubeChannelDetail {
    override val id: YouTubeChannel.Id = YouTubeChannel.Id("channel_$id")
    override val uploadedPlayList: YouTubePlaylist.Id =
        YouTubePlaylist.Id("playlist_${this.id.value}")
    override val title: String = "channel$id"
    override val iconUrl: String = ""
    override val bannerUrl: String = ""
    override val subscriberCount: BigInteger = BigInteger.ONE
    override val isSubscriberHidden: Boolean = false
    override val videoCount: BigInteger = BigInteger.ONE
    override val viewsCount: BigInteger = BigInteger.ONE
    override val publishedAt: Instant = Instant.EPOCH
    override val customUrl: String = ""
    override val keywords: Collection<String> = emptyList()
    override val description: String = ""
}

private fun video(id: Int, channel: YouTubeChannel): YouTubeVideo = object : YouTubeVideo {
    override val id: YouTubeVideo.Id = YouTubeVideo.Id("video_$id")
    override val channel: YouTubeChannel = channel
    override val liveBroadcastContent: YouTubeVideo.BroadcastType =
        YouTubeVideo.BroadcastType.UPCOMING
    override val title: String = ""
    override val thumbnailUrl: String = ""
    override val scheduledStartDateTime: Instant? = Instant.EPOCH
    override val scheduledEndDateTime: Instant? = null
    override val actualStartDateTime: Instant? = null
    override val actualEndDateTime: Instant? = null
    override val description: String = ""
    override val viewerCount: BigInteger? = BigInteger.ONE
}

private fun playlistItem(
    id: Int,
    channelDetail: YouTubeChannelDetail,
    videoId: YouTubeVideo.Id,
): YouTubePlaylistItem = object : YouTubePlaylistItem {
    override val id: YouTubePlaylistItem.Id =
        YouTubePlaylistItem.Id("playlistItem_${channelDetail.id.value}_$id")
    override val playlistId: YouTubePlaylist.Id = channelDetail.uploadedPlayList!!
    override val channel: YouTubeChannel = channelDetail
    override val videoId: YouTubeVideo.Id = videoId
    override val title: String = "title"
    override val thumbnailUrl: String = ""
    override val description: String = ""
    override val videoOwnerChannelId: YouTubeChannel.Id? = null
    override val publishedAt: Instant = Instant.EPOCH
}

private fun subscription(id: Int, channel: YouTubeChannel): YouTubeSubscription =
    object : YouTubeSubscription {
        override val id: YouTubeSubscription.Id = YouTubeSubscription.Id("$id")
        override val channel: YouTubeChannel = channel
        override val order: Int = id
        override val subscribeSince: Instant = Instant.EPOCH
    }

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DateTimeModule::class],
)
interface FakeDateTimeProviderModule {
    companion object {
        var instant: Instant? = Instant.parse("2025-04-20T00:00:00Z")

        @Provides
        @Singleton
        fun provideDateTimeProvider(): DateTimeProvider = object : DateTimeProvider {
            override fun now(): Instant = checkNotNull(instant)
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
        var subscription: Map<Pair<Int, String?>, Pair<List<YouTubeSubscription>, String?>>? =
            null
        var channel: Map<Set<YouTubeChannel.Id>, List<YouTubeChannelDetail>>? = null
        var playlistItem: Map<YouTubePlaylist.Id, List<YouTubePlaylistItem>>? = null
        var video: List<YouTubeVideo>? = null

        @Singleton
        @Provides
        fun provide(): YouTubeClient = object : YouTubeClient {
            override fun fetchSubscription(
                pageSize: Long,
                offset: Int,
                token: String?,
            ): YouTubeClient.Response<YouTubeSubscription> {
                logD { "fetchSubscription: $offset, $token" }
                val res = subscription?.get(offset to token)!!
                return YouTubeClient.Response(res.first, res.second)
            }

            override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): YouTubeClient.Response<YouTubeChannelDetail> {
                logD { "fetchChannelList: $ids" }
                return YouTubeClient.Response(channel?.get(ids)!!)
            }

            override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): YouTubeClient.Response<YouTubeVideo> {
                logD { "fetchVideoList: $ids" }
                return YouTubeClient.Response(ids.mapNotNull { id -> video?.find { it.id == id } })
            }

            override fun fetchPlaylistItems(
                id: YouTubePlaylist.Id,
                maxResult: Long,
            ): YouTubeClient.Response<YouTubePlaylistItem> {
                logD { "fetchPlaylistItems: $id, $maxResult" }
                return YouTubeClient.Response(playlistItem?.get(id)!!)
            }

            override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): YouTubeClient.Response<YouTubePlaylist> =
                throw NotImplementedError()

            override fun fetchChannelSection(id: YouTubeChannel.Id): YouTubeClient.Response<YouTubeChannelSection> =
                throw NotImplementedError()

            override fun fetchLiveChannelLogs(
                channelId: YouTubeChannel.Id,
                publishedAfter: Instant?,
                maxResult: Long?,
                token: String?,
            ): YouTubeClient.Response<YouTubeChannelLog> = throw NotImplementedError()
        }

        @Singleton
        @Provides
        fun provideNewChooseAccountIntentProvider(): NewChooseAccountIntentProvider =
            object : NewChooseAccountIntentProvider {
                override fun invoke(): Intent = throw NotImplementedError()
            }
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [YouTubeAccountDataSourceModule::class],
)
interface FakeYouTubeAccountModule {
    companion object {
        var account: String? = null

        @Singleton
        @Provides
        fun provide(): YouTubeAccountDataStore.Local = object : YouTubeAccountDataStore.Local {
            override fun getAccount(): String? = account
            override val googleAccount: Flow<String?> get() = throw NotImplementedError()
            override suspend fun putAccount(account: String) = throw NotImplementedError()
            override suspend fun clearAccount() = throw NotImplementedError()
        }
    }

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindAccountRepository(repository: YouTubeAccountRepository): AccountRepository
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DbModule::class],
)
interface InMemoryDbModule {
    companion object {
        @Provides
        @Singleton
        fun provideInMemoryDb(@ApplicationContext context: Context): AppDatabase =
            AppDatabase.createInMemory(context)
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineModule::class],
)
interface TestCoroutineScopeModule {
    companion object {
        var testScheduler: TestCoroutineScheduler? = null

        @Provides
        @Singleton
        fun provideIoCoroutineScope(): CoroutineScope =
            CoroutineScope(StandardTestDispatcher(testScheduler))

        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)
    }
}
