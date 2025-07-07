package com.freshdigitable.yttt.feature.timetable

import app.cash.turbine.test
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.feature.timetable.FakeYouTubeClientImpl.Companion.setup
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.fromRemote
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
    val testScope = TestCoroutineScopeRule()

    @get:Rule(order = 2)
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
        FakeYouTubeClientModule.clean()
    }

    @Test
    fun earlyReturn_whenNoAccount() = testScope.runTest {
        // setup
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
    fun videoAndSubscriptionAreEmpty_whenSuccess() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
            subscription = { offset, token ->
                if (offset == 0 && token == null) {
                    NetworkResponse.create(emptyList<YouTubeSubscription>().toUpdatable())
                } else {
                    throw AssertionError()
                }
            },
        )
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
    fun failedToGetSubscription_returnsFailure() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
            subscription = { _, _ ->
                throw YouTubeException(
                    500, "Server Internal Error", cacheControl = CacheControl.EMPTY,
                )
            },
        )
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isFailure {
            it.isInstanceOf(YouTubeException::class.java)
        }
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun videoFromNewPlaylistItem_returns20Videos() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(10, 2, current)
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
    fun failedToGetChannelDetails_returnsFailure() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(10, 2, current).apply {
            channel = {
                throw YouTubeException(
                    500, "Server Internal Error", cacheControl = CacheControl.EMPTY,
                )
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isFailure {
            it.isInstanceOf(YouTubeException::class.java)
        }
        localSource.videos.test {
            assertThat(awaitItem()).isEmpty()
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(current)
    }

    @Test
    fun failedToGetPlaylistItem_returnsFailure() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(10, 2, current).apply {
            val base = playlistItem!!
            playlistItem = { id ->
                if (id.value.contains("1")) throw YouTubeException(
                    500, "Server Internal Error", cacheControl = CacheControl.create(current, null),
                )
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
    fun getPlaylistItemReceivesNotFound_resultIsRecovered() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(10, 2, current).apply {
            val base = playlistItem!!
            playlistItem = { id ->
                if (id.value.contains("1")) throw YouTubeException(
                    404, "Not Found", cacheControl = CacheControl.create(current, null),
                )
                else base.invoke(id)
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
    }

    @Test
    fun failedToGetVideoDetail_returnsFailure() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(10, 2, current).apply {
            val base = video!!
            video = { id ->
                if (id.any { it.value.contains("1") })
                    throw YouTubeException(
                        500, "Server Internal Error", cacheControl = CacheControl.EMPTY,
                    )
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
    fun videoFromNewPlaylistItem_fetch2PagesOfSubscription_returns200Videos() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(100, 2, current)
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
    fun failedToGetChannelDetailsAt2ndPageOfSubscription_returnsFailure() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        FakeYouTubeClientModule.setup(100, 2, current).apply {
            val channel = channel
            var page = 0
            this.channel = {
                if (page == 0) {
                    page++
                    channel!!.invoke(it)
                } else throw YouTubeException(
                    500, "Server Internal Error", cacheControl = CacheControl.EMPTY,
                )
            }
        }
        hiltRule.inject()
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isFailure {
            it.isInstanceOf(YouTubeException::class.java)
        }
    }

    @Test
    fun videoFromNewPlaylistItem_has1Subscription_fetch2PagesOfSubscription_returns200Videos() =
        testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            val fake = FakeYouTubeClientModule.setup(1, 2, current)
            hiltRule.inject()
            sut.invoke()
            advanceUntilIdle()
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = { fake.setup(100, 2, it, ::video) }
                instant = current + Duration.ofHours(3)
            }
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
    fun videoFromNewPlaylistItem_update10Subscription() = testScope.runTest {
        // setup
        FakeYouTubeAccountModule.account = "account"
        val fakeClient = FakeYouTubeClientModule.setup(10, 2, current)
        hiltRule.inject()
        sut.invoke()
        advanceUntilIdle()

        FakeDateTimeProviderModule.apply {
            onTimeAdvanced = {
                fakeClient.setup(10, 3, it) { i, c ->
                    video(i, c, YouTubeVideo.BroadcastType.LIVE)
                }
            }
            instant = current + Duration.ofHours(3)
        }
        // exercise
        val actual = sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(actual).isSuccess()
        localSource.videos.test {
            val a = awaitItem()
            assertThat(a).hasSize(30)
            assertThat(a.map { it.isNowOnAir() }.all { it }).isTrue()
        }
        assertThat(localSource.subscriptionsFetchedAt).isEqualTo(Instant.parse("2025-04-20T03:00:00Z"))
    }
}

private fun FakeYouTubeClientModule.Companion.setup(
    subscriptionCount: Int,
    itemsPerPlaylist: Int,
    current: Instant,
): FakeYouTubeClientImpl = FakeYouTubeClientImpl()
    .apply { setup(subscriptionCount, itemsPerPlaylist, current) { i, c -> video(i, c) } }
    .also { client = it }

internal fun video(
    id: Int,
    channel: YouTubeChannel,
    liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
): YouTubeVideo = object : YouTubeVideo {
    override val id: YouTubeVideo.Id = YouTubeVideo.Id("${channel.id.value}-video_$id")
    override val channel: YouTubeChannel = channel
    override val liveBroadcastContent: YouTubeVideo.BroadcastType = liveBroadcastContent
    override val title: String = ""
    override val thumbnailUrl: String = ""
    override val scheduledStartDateTime: Instant? = Instant.EPOCH
    override val scheduledEndDateTime: Instant? = null
    override val actualStartDateTime: Instant? =
        if (liveBroadcastContent == YouTubeVideo.BroadcastType.LIVE) Instant.EPOCH else null
    override val actualEndDateTime: Instant? = null
    override val description: String = ""
    override val viewerCount: BigInteger? = BigInteger.ONE
}

private fun playlistItem(
    id: Int,
    channelDetail: YouTubeChannelDetail,
    videoId: YouTubeVideo.Id,
): YouTubePlaylistItem = FakeYouTubeClient.playlistItem(
    id = YouTubePlaylistItem.Id("playlistItem_${channelDetail.id.value}_$id"),
    playlistId = channelDetail.uploadedPlayList!!,
    videoId = videoId,
)

private fun subscription(id: Int, channel: YouTubeChannel): YouTubeSubscription =
    object : YouTubeSubscription {
        override val id: YouTubeSubscription.Id = YouTubeSubscription.Id("$id")
        override val channel: YouTubeChannel = channel
        override val order: Int = id
        override val subscribeSince: Instant = Instant.EPOCH
    }

private class FakeYouTubeClientImpl(
    var subscription: ((Int, String?) -> NetworkResponse<List<YouTubeSubscription>>)? = null,
    var channel: ((Set<YouTubeChannel.Id>) -> Updatable<List<YouTubeChannelDetail>>)? = null,
    var playlistItem: ((YouTubePlaylist.Id) -> Updatable<List<YouTubePlaylistItem>>)? = null,
    var video: ((Set<YouTubeVideo.Id>) -> Updatable<List<YouTubeVideo>>)? = null,
) : FakeYouTubeClient() {
    companion object {
        fun FakeYouTubeClientImpl.setup(
            subscriptionCount: Int,
            itemsPerPlaylist: Int,
            current: Instant,
            videoFactory: (Int, YouTubeChannelDetail) -> YouTubeVideo,
        ) {
            val channelDetail = (1..subscriptionCount)
                .map { channelDetail(it) }
            val videos = channelDetail.flatMap { c ->
                (1..itemsPerPlaylist).map { videoFactory(it, c) }
            }
            channel = { id ->
                val c = channelDetail.associateBy { it.id }
                id.mapNotNull { c[it] }.toUpdatable(CacheControl.fromRemote(current))
            }
            val chunked = channelDetail.chunked(50)
            val subs = chunked.mapIndexed { i, c ->
                val tokenMatcher = if (i == 0) null else "token$i"
                val nextToken = if (i == chunked.size - 1) null else "token${i + 1}"
                (i * 50 to tokenMatcher) to NetworkResponse.create(
                    c.mapIndexed { j, s -> subscription(j, s) }.toUpdatable(current), nextToken
                )
            }.toMap()
            subscription = { offset, token -> subs[offset to token]!! }
            val v = videos.associateBy { it.id }
            video = { id -> id.mapNotNull { v[it] }.toUpdatable(current) }
            val pi = channelDetail.associate { c ->
                c.uploadedPlayList!! to videos.filter { it.channel.id == c.id }
                    .mapIndexed { i, v -> playlistItem(i, c, v.id) }
                    .toUpdatable(CacheControl.fromRemote(current))
            }
            playlistItem = { pi[it]!! }
        }
    }

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
        return NetworkResponse.create(channel!!.invoke(ids))
    }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        logD { "fetchVideoList: $ids" }
        check(ids.size <= 50) { "exceeds upper limit: ${ids.size}" }
        return NetworkResponse.create(video!!.invoke(ids))
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): NetworkResponse<List<YouTubePlaylistItem>> {
        logD { "fetchPlaylistItems: $id, $maxResult" }
        return NetworkResponse.create(playlistItem!!.invoke(id))
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

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
