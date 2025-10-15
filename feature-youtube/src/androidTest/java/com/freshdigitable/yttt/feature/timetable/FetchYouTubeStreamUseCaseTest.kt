package com.freshdigitable.yttt.feature.timetable

import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.LiveTimelineItem
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.feature.timetable.FakeYouTubeClientImpl.Companion.setup
import com.freshdigitable.yttt.feature.timetable.FakeYouTubeClientImpl.Companion.update
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.internalServerError
import com.freshdigitable.yttt.test.notFound
import com.freshdigitable.yttt.test.notModified
import com.freshdigitable.yttt.test.testWithRefresh
import com.freshdigitable.yttt.test.toTestPager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoMap
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@RunWith(Enclosed::class)
class FetchYouTubeStreamUseCaseTest {
    @HiltAndroidTest
    class Init : Base() {
        private val current = Instant.parse("2025-04-20T00:00:00Z")

        @Before
        fun setup() {
            FakeDateTimeProviderModule.instant = current
            FakeYouTubeAccountModule.account = null
            FakeYouTubeClientModule.clean()
        }

        @After
        fun tearDown() {
            FakeDateTimeProviderModule.clear()
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
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun videoAndSubscriptionAreEmpty_whenSuccess() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
                subscription = { token, _ ->
                    if (token == null) {
                        NetworkResponse.create(
                            emptyList<YouTubeSubscription>().toUpdatable(),
                            eTag = "empty_etag",
                        )
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
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetSubscription_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
                subscription = { _, _ -> throw YouTubeException.internalServerError() },
            )
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.EPOCH
        }

        @Test
        fun videoFromNewPlaylistItem_returns20Videos() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(10, 2, current) { i, c ->
                video(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage {
                getPages().flatten() shouldHaveSize 20
            }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.fetchUpdatableVideoIds(current + Duration.ofHours(3)) shouldHaveSize 20
        }

        @Test
        fun failedToGetChannelDetails_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(10, 2, current).apply {
                channel = { throw YouTubeException.internalServerError() }
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
        }

        @Test
        fun failedToGetPlaylistItem_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(10, 2, current).apply {
                val base = playlistItem!!
                playlistItem = { id ->
                    if (id.value.contains("1")) throw YouTubeException.internalServerError(
                        cacheControl = CacheControl.create(current, null),
                    )
                    else base.invoke(id)
                }
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
        }

        @Test
        fun getPlaylistItemReceivesNotFound_resultIsRecovered() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(10, 2, current).apply {
                val base = playlistItem!!
                playlistItem = { id ->
                    if (id.value.contains("1")) throw YouTubeException.notFound(
                        cacheControl = CacheControl.create(current, null),
                    )
                    else base.invoke(id)
                }
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
        }

        @Test
        fun failedToGetVideoDetail_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(10, 2, current).apply {
                video = { id ->
                    if (id.any { it.value.contains("1") }) throw YouTubeException.internalServerError()
                    else videoDefault.invoke(id)
                }
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
        }

        @Test
        fun videoFromNewPlaylistItem_fetch2PagesOfSubscription_returns200Videos() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(100, 2, current) { i, c ->
                video(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize 200 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
        }

        @Test
        fun failedToGetChannelDetailsAt2ndPageOfSubscription_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            FakeYouTubeClientModule.setup(100, 2, current).apply {
                var page = 0
                this.channel = {
                    if (page == 0) {
                        page++
                        channelDefault(it)
                    } else throw YouTubeException.internalServerError()
                }
            }
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
        }
    }

    @HiltAndroidTest
    class AfterInit : Base() {
        @Inject
        lateinit var localSource: YouTubeDataSource.Local
        private val current = Instant.parse("2025-04-20T00:00:00Z")
        private lateinit var fakeClient: FakeYouTubeClientImpl

        override val testScope = TestCoroutineScopeRule(
            setup = {
                FakeYouTubeAccountModule.account = "account"
                FakeDateTimeProviderModule.instant = current
                fakeClient = FakeYouTubeClientModule.setup(10, 2, current)
                hiltRule.inject()
                sut.invoke()
            },
        )

        @After
        fun tearDown() {
            FakeDateTimeProviderModule.clear()
        }

        @Test
        fun videoFromNewPlaylistItem_has1Subscription_fetch2PagesOfSubscription_returns200Videos() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(100, 2, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize 200 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun subscriptionIsRemoved_returns18VideosAnd9Subscriptions() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(9, 2, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            localSource.fetchSubscriptionIds() shouldHaveSize 9
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize 18 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun videoFromNewPlaylistItem_update10Subscription() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(10, 3, it) { i, c ->
                        video(
                            i,
                            c,
                            YouTubeVideo.BroadcastType.LIVE,
                            actualStartDateTime = current - Duration.ofHours(2),
                        )
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testForAllPage { getPages().flatten() shouldHaveSize 30 }
            livePagingSource.upcoming.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun playlistItemIsNotModified_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(10, 3, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                    val base = fakeClient.playlistItem!!
                    fakeClient.playlistItem = { id ->
                        if (id.value.contains("1")) throw YouTubeException.notModified(
                            cacheControl = CacheControl.create(it, null),
                        )
                        else base.invoke(id)
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize 28 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }
    }

    @HiltAndroidTest
    class SubscriptionIsNotModified : Base() {
        private val current = Instant.parse("2025-04-20T00:00:00Z")
        private lateinit var fakeClient: FakeYouTubeClientImpl

        override val testScope = TestCoroutineScopeRule(
            setup = {
                FakeYouTubeAccountModule.account = "account"
                FakeDateTimeProviderModule.instant = current
                fakeClient = FakeYouTubeClientModule.setup(150, 2, current)
                hiltRule.inject()
                sut.invoke()
                extendedSource.findSubscriptionQuery(0).shouldNotBeNull()
                    .eTag.shouldNotBeNull()
            },
        )

        @After
        fun tearDown() {
            FakeDateTimeProviderModule.clear()
        }

        @Test
        fun allPageIsNotModified() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.update(3, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            val networkRes = fakeClient.wrapSubscriptionAsResult()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize (150 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].shouldBeFailureOfYouTubeException(statusCode = 304)
        }

        @Test
        fun firstPageIsNotModified() = testScope.runTest {
            // setup
            fakeClient.channelDetail = fakeClient.channelDetail.filterIndexed { i, _ -> i != 50 }
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.update(3, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            val networkRes = fakeClient.wrapSubscriptionAsResult()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize (149 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeSuccess()
            networkRes[2].shouldBeSuccess()
        }

        @Test
        fun firstAndSecondPagesAreNotModified() = testScope.runTest {
            // setup
            fakeClient.channelDetail = fakeClient.channelDetail.filterIndexed { i, _ -> i != 100 }
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.update(3, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            val networkRes = fakeClient.wrapSubscriptionAsResult()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten() shouldHaveSize (149 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].shouldBeSuccess()
        }

        @Test
        fun failedToFetchLastPage_returnFailure() = testScope.runTest {
            // setup
            fakeClient.channelDetail = fakeClient.channelDetail.filterIndexed { i, _ -> i != 100 }
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.update(3, it) { i, c ->
                        video(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            val s = checkNotNull(fakeClient.subscription)
            fakeClient.subscription = { t, e ->
                val res = s(t, e)
                if (res.nextPageToken != null) res
                else throw YouTubeException.internalServerError(
                    cacheControl = CacheControl.fromRemote(FakeDateTimeProviderModule.instant!!),
                )
            }
            val networkRes = fakeClient.wrapSubscriptionAsResult()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming.testForAllPage { getPages().flatten().size.shouldBeGreaterThan(300) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].shouldBeFailureOfYouTubeException(statusCode = 500)
        }
    }

    abstract class Base() {
        @get:Rule(order = 0)
        val hiltRule = HiltAndroidRule(this)

        @get:Rule(order = 1)
        open val testScope = TestCoroutineScopeRule()

        @get:Rule(order = 2)
        val traceRule = AppTraceVerifier()

        @Inject
        lateinit var extendedSource: YouTubeDataSource.Extended

        @Inject
        lateinit var livePagingSource: LiveDataPagingSource

        @Inject
        internal lateinit var sut: FetchYouTubeStreamUseCase

        suspend fun PagingSource<Int, out LiveTimelineItem>.testForAllPage(
            verify: suspend TestPager<Int, out LiveTimelineItem>.() -> Unit,
        ) {
            val testPager = toTestPager().apply {
                var res: @JvmSuppressWildcards PagingSource.LoadResult<Int, out LiveTimelineItem>? = refresh()
                while ((res as? PagingSource.LoadResult.Page)?.nextKey != null) {
                    res = append()
                }
            }
            testPager.verify()
        }
    }
}

private fun FakeYouTubeClientImpl.wrapSubscriptionAsResult(): List<Result<*>> {
    val networkRes = mutableListOf<Result<*>>()
    val f = checkNotNull(subscription)
    subscription = { t, e ->
        val res = runCatching { f(t, e) }
        networkRes.add(res)
        if (res.isSuccess) res.getOrNull()!! else throw res.exceptionOrNull()!!
    }
    return networkRes
}

internal inline fun <reified T> Result<T>.shouldBeFailureOfYouTubeException(statusCode: Int) {
    shouldBeFailure<YouTubeException>().should { it.statusCode shouldBe statusCode }
}

private fun FakeYouTubeClientModule.Companion.setup(
    subscriptionCount: Int,
    itemsPerPlaylist: Int,
    current: Instant,
    videoFactory: (Int, YouTubeChannelDetail) -> YouTubeVideo = ::video,
): FakeYouTubeClientImpl = FakeYouTubeClientImpl()
    .apply { setup(subscriptionCount, itemsPerPlaylist, current, videoFactory) }
    .also { client = it }

internal fun video(
    id: Int,
    channel: YouTubeChannel,
    liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
    scheduleStartDateTime: Instant = Instant.EPOCH,
    actualStartDateTime: Instant = Instant.EPOCH,
): YouTubeVideo = object : YouTubeVideo {
    override val id: YouTubeVideo.Id = YouTubeVideo.Id("${channel.id.value}-video_$id")
    override val channel: YouTubeChannel = channel
    override val liveBroadcastContent: YouTubeVideo.BroadcastType = liveBroadcastContent
    override val title: String = ""
    override val thumbnailUrl: String = ""
    override val scheduledStartDateTime: Instant? = scheduleStartDateTime
    override val scheduledEndDateTime: Instant? = null
    override val actualStartDateTime: Instant? =
        if (liveBroadcastContent == YouTubeVideo.BroadcastType.LIVE) actualStartDateTime else null
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

private class FakeYouTubeClientImpl(
    var subscription: ((String?, String?) -> NetworkResponse<List<YouTubeSubscription>>)? = null,
    var channel: ((Set<YouTubeChannel.Id>) -> Updatable<List<YouTubeChannelRelatedPlaylist>>)? = null,
    var playlistItem: ((YouTubePlaylist.Id) -> Updatable<List<YouTubePlaylistItem>>)? = null,
    var video: ((Set<YouTubeVideo.Id>) -> Updatable<List<YouTubeVideo>>)? = null,
) : FakeYouTubeClient() {
    var current: Instant? = null
    var channelDetail: List<YouTubeChannelDetail> = emptyList()
    var videos: List<YouTubeVideo> = emptyList()

    companion object {
        private val md = MessageDigest.getInstance("SHA-256")
        fun FakeYouTubeClientImpl.setup(
            subscriptionCount: Int,
            itemsPerPlaylist: Int,
            current: Instant,
            videoFactory: (Int, YouTubeChannelDetail) -> YouTubeVideo,
        ) {
            logD { "setup:$subscriptionCount,$itemsPerPlaylist,$current,$videoFactory" }
            this.current = current
            channelDetail = (1..subscriptionCount).map { channelDetail(it) }
                .sortedBy { it.title.lowercase() }
            update(itemsPerPlaylist, current, videoFactory)
        }

        fun FakeYouTubeClientImpl.update(
            itemsPerPlaylist: Int,
            current: Instant,
            videoFactory: (Int, YouTubeChannelDetail) -> YouTubeVideo,
        ) {
            this.current = current
            val chunked = channelDetail.chunked(50)
            val subRes = chunked.mapIndexed { i, c ->
                val sub = c.map { subscription("s_${it.id.value}", it) }
                val tokenKey = if (i == 0) null else "token$i"
                val nextToken = if (i == chunked.size - 1) null else "token${i + 1}"
                val etag = md.run {
                    reset()
                    digest(sub.joinToString("") { it.id.value }.toByteArray()).toHexString()
                }
                tokenKey to NetworkResponse.create(
                    sub.toUpdatable(current),
                    nextToken,
                    etag,
                )
            }.toMap()
            subscription = { token, etag ->
                val r = checkNotNull(subRes[token])
                if (r.eTag != etag) r
                else throw YouTubeException.notModified(
                    cacheControl = CacheControl.create(current, null),
                )
            }
            videos = channelDetail.flatMap { c ->
                (1..itemsPerPlaylist).map { videoFactory(it, c) }
            }
            val pi = channelDetail.associate { c ->
                c.uploadedPlayList!! to videos.filter { it.channel.id == c.id }
                    .mapIndexed { i, v -> playlistItem(i, c, v.id) }
                    .toUpdatable(CacheControl.fromRemote(current))
            }
            playlistItem = { pi[it]!! }
        }
    }

    override fun fetchSubscription(query: YouTubeSubscriptionQuery): NetworkResponse<List<YouTubeSubscription>> {
        logD { "fetchSubscription: $query" }
        return subscription!!.invoke(query.nextPageToken, query.eTag)
    }

    val channelDefault: (Set<YouTubeChannel.Id>) -> Updatable<List<YouTubeChannelRelatedPlaylist>> = { id ->
        val c = channelDetail.associateBy { it.id }
        id.mapNotNull { c[it] }.toUpdatable(CacheControl.fromRemote(current!!))
    }

    override fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): NetworkResponse<List<YouTubeChannelRelatedPlaylist>> {
        logD { "fetchChannelRelatedPlaylistList: $ids" }
        val channel = this.channel ?: channelDefault
        return NetworkResponse.create(channel(ids))
    }

    val videoDefault: (Set<YouTubeVideo.Id>) -> Updatable<List<YouTubeVideo>> = { id ->
        val v = videos.associateBy { it.id }
        id.mapNotNull { v[it] }.toUpdatable(current)
    }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        logD { "fetchVideoList: $ids" }
        check(ids.size <= 50) { "exceeds upper limit: ${ids.size}" }
        val video = this.video ?: videoDefault
        return NetworkResponse.create(video(ids))
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
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
