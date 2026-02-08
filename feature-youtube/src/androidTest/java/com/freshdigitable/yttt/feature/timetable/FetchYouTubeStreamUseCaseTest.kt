package com.freshdigitable.yttt.feature.timetable

import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.feature.timetable.FakeYouTubeClientImpl.Companion.setup
import com.freshdigitable.yttt.feature.timetable.FakeYouTubeClientImpl.Companion.update
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.ChannelItemJson
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.PlaylistItemJson
import com.freshdigitable.yttt.test.SubscriptionItemJson
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.VideoJson
import com.freshdigitable.yttt.test.YouTubeResponseJson
import com.freshdigitable.yttt.test.internalServerError
import com.freshdigitable.yttt.test.notFound
import com.freshdigitable.yttt.test.notModified
import com.freshdigitable.yttt.test.subscriptionJson
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
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun videoAndSubscriptionAreEmpty_whenSuccess() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                subscription = { token ->
                    if (token == null) {
                        subscriptionJson(eTag = "empty_etag", pageToken = null, size = 0)
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
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetSubscription_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                subscription = { throw YouTubeException.internalServerError() },
            )
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.EPOCH
        }

        @Test
        fun videoFromNewPlaylistItem_returns20Videos() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                FakeYouTubeClientModule.setup(10, 2) { i, c ->
                    videoJson(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
                },
            )
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testForAllPage {
                getPages().flatten() shouldHaveSize 20
            }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.fetchUpdatableVideoIds(current + Duration.ofHours(3)) shouldHaveSize 20
        }

        @Test
        fun failedToGetChannelDetails_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                FakeYouTubeClientModule.setup(10, 2).apply {
                    channel = { throw YouTubeException.internalServerError() }
                },
            )
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
        }

        @Test
        fun failedToGetPlaylistItem_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                FakeYouTubeClientModule.setup(10, 2).apply {
                    val base = playlistItem!!
                    playlistItem = { id ->
                        if (id.value.contains("1")) {
                            throw YouTubeException.internalServerError()
                        } else {
                            base.invoke(id)
                        }
                    }
                },
            )
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
            server.setClient(
                FakeYouTubeClientModule.setup(10, 2).apply {
                    val base = playlistItem!!
                    playlistItem = { id ->
                        if (id.value.contains("1")) {
                            throw YouTubeException.notFound()
                        } else {
                            base.invoke(id)
                        }
                    }
                },
            )
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
            server.setClient(
                FakeYouTubeClientModule.setup(10, 2).apply {
                    video = { id ->
                        if (id.any { it.value.contains("1") }) {
                            throw YouTubeException.internalServerError()
                        } else {
                            videoDefault.invoke(id)
                        }
                    }
                },
            )
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
            server.setClient(
                FakeYouTubeClientModule.setup(100, 2) { i, c ->
                    videoJson(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
                },
            )
            hiltRule.inject()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testForAllPage { getPages().flatten() shouldHaveSize 200 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
        }

        @Test
        fun failedToGetChannelDetailsAt2ndPageOfSubscription_returnsFailure() = testScope.runTest {
            // setup
            FakeYouTubeAccountModule.account = "account"
            server.setClient(
                FakeYouTubeClientModule.setup(100, 2).apply {
                    var page = 0
                    this.channel = { (id, part) ->
                        if (page == 0) {
                            page++
                            channelDefault(id to part)
                        } else {
                            throw YouTubeException.internalServerError()
                        }
                    }
                },
            )
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
                fakeClient = FakeYouTubeClientModule.setup(10, 2)
                server.setClient(fakeClient)
                hiltRule.inject()
                extendedSource.deleteAllTables()
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
                    fakeClient.setup(100, 2) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
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
            livePagingSource.upcoming(dateTimeProvider.now()).testForAllPage {
                getPages().flatten() shouldHaveSize 200
            }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun subscriptionIsRemoved_returns18VideosAnd9Subscriptions() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(9, 2) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
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
            livePagingSource.upcoming(dateTimeProvider.now()).testForAllPage { getPages().flatten() shouldHaveSize 18 }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun videoFromNewPlaylistItem_update10Subscription() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(10, 3) { i, c ->
                        videoJson(
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
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
        }

        @Test
        fun playlistItemIsNotModified_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = {
                    fakeClient.setup(10, 3) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                    val base = fakeClient.playlistItem!!
                    fakeClient.playlistItem = { id ->
                        if (id.value.contains("1")) {
                            throw YouTubeException.notModified()
                        } else {
                            base.invoke(id)
                        }
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
            livePagingSource.upcoming(dateTimeProvider.now()).testForAllPage { getPages().flatten() shouldHaveSize 28 }
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
                fakeClient = FakeYouTubeClientModule.setup(150, 2)
                server.setClient(fakeClient)
                hiltRule.inject()
                extendedSource.deleteAllTables()
                sut.invoke()
                extendedSource.findSubscriptionQuery(0).shouldNotBeNull().eTag.shouldNotBeNull()
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
                    fakeClient.update(3) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            server.isLogging = true
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now())
                .testForAllPage { getPages().flatten() shouldHaveSize (150 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            val networkRes = server.findRecordedResponse(MockServerRule.PATH_SUBSCRIPTION).map { it.second }
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
                    fakeClient.update(3) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            server.isLogging = true
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now())
                .testForAllPage { getPages().flatten() shouldHaveSize (149 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            val networkRes = server.findRecordedResponse(MockServerRule.PATH_SUBSCRIPTION).map { it.second }
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
                    fakeClient.update(3) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            server.isLogging = true
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now())
                .testForAllPage { getPages().flatten() shouldHaveSize (149 * 3) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe Instant.parse("2025-04-20T03:00:00Z")
            val networkRes = server.findRecordedResponse(MockServerRule.PATH_SUBSCRIPTION).map { it.second }
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
                    fakeClient.update(3) { i, c ->
                        videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
                    }
                }
                instant = current + Duration.ofHours(3)
            }
            val s = checkNotNull(fakeClient.subscription)
            fakeClient.subscription = { t ->
                val index = fakeClient.subsTable.indexOfFirst { it.first == t }
                if (index == fakeClient.subsTable.lastIndex) {
                    throw YouTubeException.internalServerError()
                } else {
                    s(t)
                }
            }
            server.isLogging = true
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailureOfYouTubeException(statusCode = 500)
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now())
                .testForAllPage { getPages().flatten().size.shouldBeGreaterThan(300) }
            livePagingSource.freeChat.testWithRefresh { data.shouldBeEmpty() }
            extendedSource.subscriptionsFetchedAt shouldBe current
            val networkRes = server.findRecordedResponse(MockServerRule.PATH_SUBSCRIPTION).map { it.second }
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

        @get:Rule(order = 3)
        val server = MockServerRule()

        @Inject
        lateinit var extendedSource: YouTubeDataSource.Extended

        @Inject
        lateinit var livePagingSource: LiveDataPagingSource

        @Inject
        internal lateinit var sut: FetchYouTubeStreamUseCase

        @Inject
        lateinit var dateTimeProvider: DateTimeProvider
        suspend fun PagingSource<Int, out LiveVideo>.testForAllPage(
            verify: suspend TestPager<Int, out LiveVideo>.() -> Unit,
        ) {
            val testPager = toTestPager().apply {
                var res: @JvmSuppressWildcards PagingSource.LoadResult<Int, out LiveVideo>? = refresh()
                while ((res as? PagingSource.LoadResult.Page)?.nextKey != null) {
                    res = append()
                }
            }
            testPager.verify()
        }
    }
}

internal inline fun <reified T> Result<T>.shouldBeFailureOfYouTubeException(statusCode: Int) {
    shouldBeFailure<YouTubeException>().should { it.statusCode shouldBe statusCode }
}

private fun FakeYouTubeClientModule.Companion.setup(
    subscriptionCount: Int,
    itemsPerPlaylist: Int,
    videoFactory: (Int, YouTubeChannelTitle) -> VideoJson = ::videoJson,
): FakeYouTubeClientImpl =
    FakeYouTubeClientImpl().apply { setup(subscriptionCount, itemsPerPlaylist, videoFactory) }

internal fun videoJson(
    id: Int,
    channel: YouTubeChannelTitle,
    liveBroadcastContent: YouTubeVideo.BroadcastType = YouTubeVideo.BroadcastType.UPCOMING,
    scheduleStartDateTime: Instant = Instant.EPOCH,
    actualStartDateTime: Instant = Instant.EPOCH,
): VideoJson = VideoJson(
    idNum = id,
    channel = channel,
    liveBroadcastContent = liveBroadcastContent,
    scheduledStartDateTime = scheduleStartDateTime,
    actualStartDateTime = if (liveBroadcastContent == YouTubeVideo.BroadcastType.LIVE) actualStartDateTime else null,
)

private class FakeYouTubeClientImpl(
    var subscription: ((String?) -> YouTubeResponseJson)? = null,
    var channel: ((Pair<Set<YouTubeChannel.Id>, Set<String>>) -> List<ChannelItemJson>)? = null,
    var playlistItem: ((YouTubePlaylist.Id) -> List<PlaylistItemJson>)? = null,
    var video: ((Set<YouTubeVideo.Id>) -> List<VideoJson>)? = null,
) : FakeYouTubeClient {
    var channelDetail: List<ChannelItemJson> = emptyList()
    var videos: List<VideoJson> = emptyList()
    var subsTable: List<Pair<String?, List<SubscriptionItemJson>>> = emptyList()
        private set

    companion object {
        fun FakeYouTubeClientImpl.setup(
            subscriptionCount: Int,
            itemsPerPlaylist: Int,
            videoFactory: (Int, YouTubeChannelTitle) -> VideoJson,
        ) {
            logD { "setup:$subscriptionCount,$itemsPerPlaylist,$videoFactory" }
            channelDetail = (1..subscriptionCount).map { ChannelItemJson.createRelatedPlaylist(it) }
                .sortedBy { it.title.lowercase() }
            update(itemsPerPlaylist, videoFactory)
        }

        fun FakeYouTubeClientImpl.update(
            itemsPerPlaylist: Int,
            videoFactory: (Int, YouTubeChannelTitle) -> VideoJson,
        ) {
            val chunked = channelDetail.chunked(50)
            subsTable = chunked.mapIndexed { i, c ->
                val sub = c.map { SubscriptionItemJson("s_${it.id.value}", it.id.value, it.title) }
                val tokenKey = if (i == 0) null else "token$i"
                tokenKey to sub
            }
            subscription = { token ->
                val index = subsTable.indexOfFirst { it.first == token }
                val sub = subsTable[index].second
                val nextPageToken = subsTable.getOrNull(index + 1)?.first
                subscriptionJson(pageToken = nextPageToken, items = sub)
            }
            val v = channelDetail.flatMap { c ->
                (1..itemsPerPlaylist).map { videoFactory(it, c) }
            }
            val vTable = v.groupBy { it.channel.id }
            videos = v
            val pi = channelDetail.associate { c ->
                val playlistId = c.playlistId!!
                val vt = vTable[c.id]!!
                playlistId to {
                    vt.mapIndexed { i, v ->
                        PlaylistItemJson("playlistItem_${c.id.value}_$i", YouTubePlaylist.Id(playlistId), v.id.value)
                    }
                }
            }
            playlistItem = { pi[it.value]!!() }
        }
    }

    override fun fetchSubscription(nextPageToken: String?, order: String): YouTubeResponseJson {
        logD { "fetchSubscription: $nextPageToken, $order" }
        return subscription!!.invoke(nextPageToken)
    }

    val channelDefault: (Pair<Set<YouTubeChannel.Id>, Set<String>>) -> List<ChannelItemJson> = { (id, part) ->
        val table = channelDetail.associateBy { it.id }
        id.mapNotNull { i -> table[i] }
    }

    override fun fetchChannels(ids: Set<YouTubeChannel.Id>, part: Set<String>): List<ChannelItemJson> {
        logD { "fetchChannels: $ids, $part" }
        val channel = this.channel ?: channelDefault
        return channel(ids to part)
    }

    val videoDefault: (Set<YouTubeVideo.Id>) -> List<VideoJson> = { id ->
        val v = videos.associateBy { it.id }
        id.mapNotNull { v[it] }
    }

    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<VideoJson> {
        logD { "fetchVideoList: $ids" }
        check(ids.size <= 50) { "exceeds upper limit: ${ids.size}" }
        val video = this.video ?: videoDefault
        return video(ids)
    }

    override fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        eTag: String?,
    ): List<PlaylistItemJson> {
        logD { "fetchPlaylistItems: $id, $maxResult" }
        return playlistItem!!.invoke(id)
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
