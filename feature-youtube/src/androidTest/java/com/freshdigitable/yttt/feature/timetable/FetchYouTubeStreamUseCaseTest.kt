package com.freshdigitable.yttt.feature.timetable

import android.content.Intent
import app.cash.turbine.test
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.YouTubeAccountRepository
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
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.google.common.truth.Truth.assertThat
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class FetchYouTubeStreamUseCaseTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val traceRule = AppTraceVerifier()

    @Inject
    lateinit var localSource: YouTubeDataSource.Local

    @Inject
    internal lateinit var sut: FetchYouTubeStreamUseCase
    private val current = Instant.parse("2025-04-20T00:00:00Z")

    @Before
    fun setup() {
        FakeDateTimeProviderModule.instant = current
        FakeYouTubeAccountModule.account = null
        FakeRemoteSourceModule.clean()
        TestCoroutineScopeModule.testScheduler = null
    }

    @Test
    fun earlyReturn_whenNoAccount() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        traceRule.isTraceable = false
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun videoAndSubscriptionAreEmpty_whenSuccess() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.subscription = { offset, token ->
            if (offset == 0 && token == null) {
                NetworkResponse.create(emptyList())
            } else {
                throw AssertionError()
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun failedToGetSubscription_returnsFailure() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeRemoteSourceModule.subscription =
            { _, _ -> throw YouTubeException(500, "Server Internal Error") }
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).apply {
            isFailure()
            throwable().isInstanceOf(YouTubeException::class.java)
        }
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun videoFromNewPlaylistItem_returns20Videos() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(10, 2)
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            assertThat(awaitItem()).hasSize(20)
        }
    }

    @Test
    fun failedToGetChannelDetails_returnsFailure() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(10, 2)
        FakeRemoteSourceModule.channel = { throw YouTubeException(500, "Server Internal Error") }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).apply {
            isFailure()
            throwable().isInstanceOf(YouTubeException::class.java)
        }
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(current)
    }

    @Test
    fun failedToGetPlaylistItem_returnsFailure() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.apply {
            setup(10, 2)
            val base = playlistItem!!
            playlistItem = { id ->
                if (id.value.contains("1")) throw YouTubeException(500, "Server Internal Error")
                else base.invoke(id)
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isFailure()
    }

    @Test
    fun failedToGetVideoDetail_returnsFailure() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.apply {
            setup(10, 2)
            val base = video!!
            video = { id ->
                if (id.any { it.value.contains("1") })
                    throw YouTubeException(500, "Server Internal Error")
                else base.invoke(id)
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isFailure()
    }

    @Test
    fun videoFromNewPlaylistItem_fetch2PagesOfSubscription_returns200Videos() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(100, 2)
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            assertThat(awaitItem()).hasSize(200)
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(current)
    }

    @Test
    fun failedToGetChannelDetailsAt2ndPageOfSubscription_returnsFailure() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeRemoteSourceModule.setup(100, 2)
        val channel = FakeRemoteSourceModule.channel
        var page = 0
        FakeRemoteSourceModule.channel = {
            if (page == 0) {
                page++
                channel!!.invoke(it)
            } else throw YouTubeException(500, "Server Internal Error")
        }
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).apply {
            isFailure()
            throwable().isInstanceOf(YouTubeException::class.java)
        }
    }

    @Test
    fun videoFromNewPlaylistItem_has1Subscription_fetch2PagesOfSubscription_returns200Videos() =
        runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            TestCoroutineScopeModule.testScheduler = testScheduler
            FakeRemoteSourceModule.setup(1, 2)
            hiltRule.inject()
            sut.invoke()
            advanceUntilIdle()
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            FakeRemoteSourceModule.setup(100, 2)
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            localSource.videos.test {
                assertThat(awaitItem()).hasSize(200)
            }
            assertThat(localSource.subscriptionsFetchedAt).isEqualTo(Instant.parse("2025-04-20T03:00:00Z"))
        }

    @Test
    fun videoFromNewPlaylistItem_update10Subscription() = runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        TestCoroutineScopeModule.testScheduler = testScheduler
        FakeRemoteSourceModule.setup(10, 2)
        hiltRule.inject()
        sut.invoke()
        advanceUntilIdle()

        FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
        FakeRemoteSourceModule.setup(10, 3)
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            assertThat(awaitItem()).hasSize(30)
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(Instant.parse("2025-04-20T03:00:00Z"))
    }
}

private fun FakeRemoteSourceModule.Companion.setup(
    subscriptionCount: Int,
    itemsPerPlaylist: Int
) {
    val channelDetail = (1..subscriptionCount).map { channelDetail(it) }
    val videos = channelDetail.flatMap { c -> (1..itemsPerPlaylist).map { video(it, c) } }
    channel = { id ->
        val c = channelDetail.associateBy { it.id }
        id.mapNotNull { c[it] }
    }
    val chunked = channelDetail.chunked(50)
    val subs = chunked.mapIndexed { i, c ->
        val tokenMatcher = if (i == 0) null else "token$i"
        val nextToken = if (i == chunked.size - 1) null else "token${i + 1}"
        (i * 50 to tokenMatcher) to NetworkResponse.create(
            c.mapIndexed { j, s -> subscription(j, s) }, nextToken
        )
    }.toMap()
    subscription = { offset, token -> subs[offset to token]!! }
    val v = videos.associateBy { it.id }
    video = { id -> id.mapNotNull { v[it] } }
    val pi = channelDetail.associate { c ->
        c.uploadedPlayList!! to videos.filter { it.channel.id == c.id }
            .mapIndexed { i, v -> playlistItem(i, c, v.id) }
    }
    playlistItem = { pi[it]!! }
}

private fun FakeRemoteSourceModule.Companion.clean() {
    channel = null
    subscription = null
    video = null
    playlistItem = null
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
    override val id: YouTubeVideo.Id = YouTubeVideo.Id("${channel.id.value}-video_$id")
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
    replaces = [YouTubeModule::class],
)
interface FakeRemoteSourceModule {
    companion object {
        var subscription: ((Int, String?) -> NetworkResponse<List<YouTubeSubscription>>)? =
            null
        var channel: ((Set<YouTubeChannel.Id>) -> List<YouTubeChannelDetail>)? = null
        var playlistItem: ((YouTubePlaylist.Id) -> List<YouTubePlaylistItem>)? = null
        var video: ((Set<YouTubeVideo.Id>) -> List<YouTubeVideo>)? = null

        @Singleton
        @Provides
        fun provide(): YouTubeClient = object : YouTubeClient {
            override fun fetchSubscription(
                pageSize: Long,
                offset: Int,
                token: String?,
            ): NetworkResponse<List<YouTubeSubscription>> {
                logD { "fetchSubscription: $offset, $token" }
                return subscription!!.invoke(offset, token)
            }

            override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> {
                logD { "fetchChannelList: $ids" }
                check(ids.size <= 50) { "exceeds upper limit: ${ids.size}" }
                return NetworkResponse.create(item = channel!!.invoke(ids))
            }

            override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
                logD { "fetchVideoList: $ids" }
                check(ids.size <= 50) { "exceeds upper limit: ${ids.size}" }
                return NetworkResponse.create(item = video!!.invoke(ids))
            }

            override fun fetchPlaylistItems(
                id: YouTubePlaylist.Id,
                maxResult: Long,
            ): NetworkResponse<List<YouTubePlaylistItem>> {
                logD { "fetchPlaylistItems: $id, $maxResult" }
                return NetworkResponse.create(playlistItem!!.invoke(id))
            }

            override fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): NetworkResponse<List<YouTubePlaylist>> =
                throw NotImplementedError()

            override fun fetchChannelSection(id: YouTubeChannel.Id): NetworkResponse<List<YouTubeChannelSection>> =
                throw NotImplementedError()

            override fun fetchLiveChannelLogs(
                channelId: YouTubeChannel.Id,
                publishedAfter: Instant?,
                maxResult: Long?,
                token: String?,
            ): NetworkResponse<List<YouTubeChannelLog>> = throw NotImplementedError()
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

interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
