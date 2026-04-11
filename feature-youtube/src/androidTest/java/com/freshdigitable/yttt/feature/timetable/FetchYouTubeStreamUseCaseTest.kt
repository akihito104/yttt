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
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery.Order
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeException
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.expectedChannelItem
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.expectedPlaylistItem
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.expectedResponses
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.expectedSubscription
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.expectedVideoItem
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.setup
import com.freshdigitable.yttt.feature.timetable.ResponseGenerator.Companion.update
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.ChannelItemJson
import com.freshdigitable.yttt.test.ChannelItemJson.Companion.toExpectedResponse
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerDispatcher.ExpectedResponse
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.PATH_SUBSCRIPTION
import com.freshdigitable.yttt.test.PlaylistItemJson
import com.freshdigitable.yttt.test.PlaylistItemJson.Companion.eTag
import com.freshdigitable.yttt.test.ResponseJson
import com.freshdigitable.yttt.test.SubscriptionItemJson
import com.freshdigitable.yttt.test.SubscriptionItemJson.Companion.eTag
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.VideoJson
import com.freshdigitable.yttt.test.VideoJson.Companion.toExpectedResponse
import com.freshdigitable.yttt.test.YouTubeErrorJson
import com.freshdigitable.yttt.test.YouTubeMockServerDispatcher
import com.freshdigitable.yttt.test.testWithRefresh
import com.freshdigitable.yttt.test.toTestPager
import com.freshdigitable.yttt.test.youtubeChannel
import com.freshdigitable.yttt.test.youtubePlaylistItem
import com.freshdigitable.yttt.test.youtubeSubscription
import com.freshdigitable.yttt.test.youtubeVideo
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
import io.kotest.matchers.types.shouldBeInstanceOf
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
            testDispatcher.add(
                ExpectedResponse.youtubeSubscription(
                    order = Order.ALPHABETICAL,
                    items = emptyList(),
                ),
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
            testDispatcher.add(
                ExpectedResponse.youtubeSubscription(
                    order = Order.ALPHABETICAL,
                    json = YouTubeErrorJson.internalServerError(),
                ),
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
            testDispatcher.add(
                *FakeYouTubeClientModule.setup(10, 2) { i, c ->
                    videoJson(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
                }.expectedResponses(),
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
            val client = FakeYouTubeClientModule.setup(10, 2)
            testDispatcher.add(*client.expectedResponses())
            testDispatcher.add(
                ExpectedResponse.youtubeChannel(
                    query = client.channelDetail.map { it.id },
                    detail = true,
                    json = YouTubeErrorJson.internalServerError(),
                ),
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
            val client = FakeYouTubeClientModule.setup(10, 2)
            val removedPlaylistItems = client.playlistItemTable.filter { it.key.value.contains("1") }
            client.playlistItemTable = client.playlistItemTable.toMutableMap().apply {
                removedPlaylistItems.forEach { (id, _) -> remove(id) }
            }
            client.videos = client.videos.toMutableList().apply {
                val removedVideo = removedPlaylistItems.values.flatten().map { it.videoId }
                removeIf { removedVideo.contains(it.id) }
            }
            testDispatcher.add(*client.expectedResponses())
            val pItem = removedPlaylistItems.keys.map {
                ExpectedResponse.youtubePlaylistItem(
                    query = it,
                    json = YouTubeErrorJson.internalServerError(),
                )
            }
            testDispatcher.add(*pItem.toTypedArray())
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
            val client = FakeYouTubeClientModule.setup(10, 2)
            val removedPlaylistItems = client.playlistItemTable.filter { it.key.value.contains("1") }
            client.playlistItemTable = client.playlistItemTable.toMutableMap().apply {
                removedPlaylistItems.forEach { (id, _) -> remove(id) }
            }
            client.videos = client.videos.toMutableList().apply {
                val removedVideo = removedPlaylistItems.values.flatten().map { it.videoId }
                removeIf { removedVideo.contains(it.id) }
            }
            testDispatcher.add(*client.expectedResponses())
            val pItem = removedPlaylistItems.keys.map {
                ExpectedResponse.youtubePlaylistItem(
                    query = it,
                    json = YouTubeErrorJson.notFound(),
                )
            }
            testDispatcher.add(*pItem.toTypedArray())
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
            val client = FakeYouTubeClientModule.setup(10, 2)
            testDispatcher.add(*client.expectedResponses())
            testDispatcher.add(
                ExpectedResponse.youtubeVideo(
                    query = client.videos.map { it.id },
                    json = YouTubeErrorJson.internalServerError(),
                ),
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
            testDispatcher.add(
                *FakeYouTubeClientModule.setup(100, 2) { i, c ->
                    videoJson(id = i, channel = c, scheduleStartDateTime = current + Duration.ofDays(1))
                }.expectedResponses(),
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
            val client = FakeYouTubeClientModule.setup(100, 2)
            testDispatcher.add(*client.expectedResponses())
            val chunked = client.channelDetail.chunked(50)
            testDispatcher.add(
                ExpectedResponse.youtubeChannel(
                    items = chunked[1],
                    detail = true,
                    json = YouTubeErrorJson.internalServerError(),
                ),
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
        private lateinit var fakeClient: ResponseGenerator

        override val testScope = TestCoroutineScopeRule(
            setup = {
                FakeYouTubeAccountModule.account = "account"
                FakeDateTimeProviderModule.instant = current
                fakeClient = FakeYouTubeClientModule.setup(10, 2)
                testDispatcher.add(*fakeClient.expectedResponses())
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
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            val oldClient = fakeClient.copy()
            fakeClient.setup(100, 2) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(
                *fakeClient.expectedVideoItem(),
                *fakeClient.expectedChannelItem { c -> !oldClient.channelDetail.map { it.id }.contains(c.id) },
            )
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
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            val oldClient = fakeClient.copy()
            fakeClient.setup(9, 2) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
                ExpectedResponse.youtubeVideo(
                    query = oldClient.videos.map { it.id },
                    items = fakeClient.videos,
                ),
            )
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
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            val oldClient = fakeClient.copy()
            fakeClient.setup(10, 3) { i, c ->
                videoJson(
                    i,
                    c,
                    YouTubeVideo.BroadcastType.LIVE,
                    actualStartDateTime = current - Duration.ofHours(2),
                )
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(*fakeClient.expectedVideoItem())
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
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            val oldClient = fakeClient.copy()
            fakeClient.setup(10, 3) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable) { pId, eTag ->
                    if (pId.value.contains("1")) {
                        ExpectedResponse.youtubePlaylistItem(
                            query = pId,
                            json = YouTubeErrorJson.notModified,
                            eTag = eTag,
                        )
                    } else {
                        null
                    }
                },
            )
            testDispatcher.addAsItem(*fakeClient.expectedVideoItem())
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
        private lateinit var fakeClient: ResponseGenerator

        override val testScope = TestCoroutineScopeRule(
            setup = {
                FakeYouTubeAccountModule.account = "account"
                FakeDateTimeProviderModule.instant = current
                fakeClient = FakeYouTubeClientModule.setup(150, 2)
                testDispatcher.add(*fakeClient.expectedResponses())
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
            val oldClient = fakeClient.copy()
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            fakeClient.update(3) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(*fakeClient.expectedVideoItem())
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
            val networkRes = server.findRecordedResponse(PATH_SUBSCRIPTION).map { it.second }
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].shouldBeFailureOfYouTubeException(statusCode = 304)
        }

        @Test
        fun firstPageIsNotModified() = testScope.runTest {
            // setup
            val oldClient = fakeClient.copy()
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            val oldChannel = oldClient.channelDetail
            fakeClient.channelDetail = oldChannel.take(50) + oldChannel.takeLast(99)
            fakeClient.update(3) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(
                *fakeClient.expectedVideoItem(),
                *oldClient.videos.filter { it.channelId == oldChannel[50].id }
                    .map { it.toExpectedResponse() }
                    .toTypedArray(),
            )
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
            val networkRes = server.findRecordedResponse(PATH_SUBSCRIPTION).map { it.second }
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].statusCode shouldBe 200
            networkRes[2].statusCode shouldBe 200
        }

        @Test
        fun firstAndSecondPagesAreNotModified() = testScope.runTest {
            // setup
            val oldClient = fakeClient.copy()
            val oldChannel = oldClient.channelDetail
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            fakeClient.channelDetail = oldChannel.take(100) + oldChannel.takeLast(49)
            fakeClient.update(3) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            testDispatcher.add(
                *fakeClient.expectedSubscription(oldClient.subsFactory),
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(
                *fakeClient.expectedVideoItem(),
                *oldClient.videos.filter { it.channelId == oldChannel[100].id }
                    .map { it.toExpectedResponse() }
                    .toTypedArray(),
            )
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
            val networkRes = server.findRecordedResponse(PATH_SUBSCRIPTION).map { it.second }
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].statusCode shouldBe 200
        }

        @Test
        fun failedToFetchLastPage_returnFailure() = testScope.runTest {
            // setup
            val oldClient = fakeClient.copy()
            val oldChannel = oldClient.channelDetail
            FakeDateTimeProviderModule.instant = current + Duration.ofHours(3)
            fakeClient.channelDetail = oldChannel.take(100) + oldChannel.takeLast(49)
            fakeClient.update(3) { i, c ->
                videoJson(i, c, scheduleStartDateTime = current + Duration.ofDays(1))
            }
            val res = fakeClient.expectedSubscription(oldClient.subsFactory)
            res[res.lastIndex] = ExpectedResponse.youtubeSubscription(
                token = fakeClient.subsFactory.last().first,
                eTag = oldClient.subsFactory.last().second.eTag(),
                order = Order.ALPHABETICAL,
                json = YouTubeErrorJson.internalServerError(),
            )
            testDispatcher.add(
                *res,
                *fakeClient.expectedPlaylistItem(oldClient.playlistItemTable),
            )
            testDispatcher.addAsItem(
                *fakeClient.expectedVideoItem(),
                *oldClient.videos.filter { it.channelId == oldChannel[100].id }
                    .map { it.toExpectedResponse() }
                    .toTypedArray(),
            )
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
            val networkRes = server.findRecordedResponse(PATH_SUBSCRIPTION).map { it.second }
            networkRes shouldHaveSize 3
            networkRes[0].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[1].shouldBeFailureOfYouTubeException(statusCode = 304)
            networkRes[2].shouldBeFailureOfYouTubeException(statusCode = 500)
        }
    }

    abstract class Base {
        @get:Rule(order = 0)
        val hiltRule = HiltAndroidRule(this)

        @get:Rule(order = 1)
        open val testScope = TestCoroutineScopeRule()

        @get:Rule(order = 2)
        val traceRule = AppTraceVerifier()

        val testDispatcher = YouTubeMockServerDispatcher()

        @get:Rule(order = 3)
        val server = MockServerRule(testDispatcher) { YouTubeModule.rootUrl = it.toString() }

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

internal fun ResponseJson.shouldBeFailureOfYouTubeException(statusCode: Int) {
    should {
        it.shouldBeInstanceOf<ExpectedResponse>()
        it.statusCode shouldBe statusCode
    }
}

private fun FakeYouTubeClientModule.Companion.setup(
    subscriptionCount: Int,
    itemsPerPlaylist: Int,
    videoFactory: (Int, YouTubeChannelTitle) -> VideoJson = ::videoJson,
): ResponseGenerator =
    ResponseGenerator().apply { setup(subscriptionCount, itemsPerPlaylist, videoFactory) }

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

private class ResponseGenerator(
    channelDetail: List<ChannelItemJson> = emptyList(),
    subsFactory: List<Pair<String?, List<SubscriptionItemJson>>> = emptyList(),
    var videos: List<VideoJson> = emptyList(),
    var playlistItemTable: Map<YouTubePlaylist.Id, List<PlaylistItemJson>> = emptyMap(),
) {
    var channelDetail: List<ChannelItemJson> = channelDetail
        set(value) {
            val v = value.sortedBy { it.title.lowercase() }
            val chunked = v.chunked(50)
            subsFactory = chunked.mapIndexed { i, c ->
                val tokenKey = if (i == 0) null else "token$i"
                val sub = c.map { createSubscriptionJson(it) }
                tokenKey to sub
            }
            field = v
        }
    var subsFactory: List<Pair<String?, List<SubscriptionItemJson>>> = subsFactory
        private set

    fun copy(): ResponseGenerator = ResponseGenerator(
        channelDetail = channelDetail.toList(),
        subsFactory = subsFactory,
        videos = videos.toList(),
        playlistItemTable = playlistItemTable.toMap(),
    )

    companion object {
        private val subsTable = mutableMapOf<YouTubeChannel.Id, SubscriptionItemJson>()
        private fun createSubscriptionJson(c: ChannelItemJson): SubscriptionItemJson =
            subsTable[c.id] ?: SubscriptionItemJson("s_${c.id.value}", c.id.value, c.title)
                .also { subsTable[c.id] = it }

        private val playlistItemTable = mutableMapOf<YouTubePlaylistItem.Id, PlaylistItemJson>()
        private fun createPlaylistItemJson(v: VideoJson, i: Int, playlistId: YouTubePlaylist.Id): PlaylistItemJson {
            val id = YouTubePlaylistItem.Id("playlistItem_${v.id.value}_$i")
            return playlistItemTable[id] ?: PlaylistItemJson(
                id,
                playlistId,
                YouTubeVideo.Id(v.id.value),
            ).also { playlistItemTable[id] = it }
        }

        fun ResponseGenerator.setup(
            subscriptionCount: Int,
            itemsPerPlaylist: Int,
            videoFactory: (Int, YouTubeChannelTitle) -> VideoJson,
        ) {
            logD { "setup:$subscriptionCount,$itemsPerPlaylist,$videoFactory" }
            channelDetail = (1..subscriptionCount).map { ChannelItemJson.createRelatedPlaylist(it) }
            update(itemsPerPlaylist, videoFactory)
        }

        fun ResponseGenerator.update(
            itemsPerPlaylist: Int,
            videoFactory: (Int, YouTubeChannelTitle) -> VideoJson,
        ) {
            val vTable = channelDetail.associateWith { c -> (1..itemsPerPlaylist).map { videoFactory(it, c) } }
            videos = vTable.values.flatten()
            playlistItemTable = vTable.map { (c, vt) ->
                val playlistId = c.playlistId!!
                playlistId to vt.mapIndexed { i, v -> createPlaylistItemJson(v, i, playlistId) }
            }.toMap()
        }

        fun ResponseGenerator.expectedResponses(): Array<ExpectedResponse> =
            expectedSubscription() + expectedPlaylistItem() + expectedVideo() + expectedChannel()

        fun ResponseGenerator.expectedSubscription(
            before: List<Pair<String?, List<SubscriptionItemJson>>> = emptyList(),
        ): Array<ExpectedResponse> {
            val tags = before.associate { (t, s) -> t to s.eTag() }
            return subsFactory.mapIndexed { index, (t, subs) ->
                val eTag = subs.eTag()
                if (eTag == tags[t]) {
                    ExpectedResponse.youtubeSubscription(
                        token = t,
                        eTag = eTag,
                        order = Order.ALPHABETICAL,
                        json = YouTubeErrorJson.notModified,
                    )
                } else {
                    ExpectedResponse.youtubeSubscription(
                        token = t,
                        eTag = tags[t],
                        order = Order.ALPHABETICAL,
                        items = subs,
                        nextPageToken = subsFactory.getOrNull(index + 1)?.first,
                    )
                }
            }.toTypedArray()
        }

        fun ResponseGenerator.expectedPlaylistItem(
            before: Map<YouTubePlaylist.Id, List<PlaylistItemJson>> = emptyMap(),
            mapIfNeed: ((YouTubePlaylist.Id, String?) -> ExpectedResponse?)? = null,
        ): Array<ExpectedResponse> {
            val table = before.map { (id, items) -> id to items.eTag() }.toMap()
            return playlistItemTable.map { (pId, items) ->
                mapIfNeed?.invoke(pId, table[pId]) ?: ExpectedResponse.youtubePlaylistItem(
                    query = pId,
                    eTag = table[pId],
                    items = items,
                )
            }.toTypedArray()
        }

        private fun ResponseGenerator.expectedVideo(
            chunkSize: Int = 50,
            filter: (VideoJson) -> Boolean = { true },
        ): Array<ExpectedResponse> = videos.filter(filter)
            .chunked(chunkSize)
            .map { ExpectedResponse.youtubeVideo(it) }
            .toTypedArray()

        fun ResponseGenerator.expectedVideoItem(
            filter: (VideoJson) -> Boolean = { true },
        ): Array<ExpectedResponse> = videos.filter(filter)
            .map { it.toExpectedResponse() }
            .toTypedArray()

        private fun ResponseGenerator.expectedChannel(
            chunkSize: Int = 50,
            filter: (ChannelItemJson) -> Boolean = { true },
        ): Array<ExpectedResponse> = channelDetail.filter(filter)
            .chunked(chunkSize)
            .map { ExpectedResponse.youtubeChannel(items = it, detail = true) }
            .toTypedArray()

        fun ResponseGenerator.expectedChannelItem(
            filter: (ChannelItemJson) -> Boolean = { true },
        ): Array<ExpectedResponse> = channelDetail.filter(filter)
            .map { it.toExpectedResponse(detail = true) }
            .toTypedArray()
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
