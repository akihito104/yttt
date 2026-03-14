package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.LiveDataPagingSource
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchException
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.di.TwitchHelixClientModule
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.TestDispatcher
import com.freshdigitable.yttt.test.TestDispatcher.ExpectedResponse
import com.freshdigitable.yttt.test.TwitchChannelScheduleJson
import com.freshdigitable.yttt.test.TwitchErrorJson
import com.freshdigitable.yttt.test.TwitchFollowedStreamJson
import com.freshdigitable.yttt.test.TwitchFollowingJson
import com.freshdigitable.yttt.test.TwitchGameJson
import com.freshdigitable.yttt.test.TwitchScheduleJson
import com.freshdigitable.yttt.test.TwitchUserJson
import com.freshdigitable.yttt.test.testWithRefresh
import com.freshdigitable.yttt.test.twitchChannelSchedule
import com.freshdigitable.yttt.test.twitchChannelsFollowed
import com.freshdigitable.yttt.test.twitchGame
import com.freshdigitable.yttt.test.twitchMe
import com.freshdigitable.yttt.test.twitchStreamsFollowed
import com.freshdigitable.yttt.test.twitchUsers
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Enclosed::class)
class FetchTwitchStreamUseCaseTest {
    @HiltAndroidTest
    class Init : Base() {
        @Before
        override fun setup() {
            FakeTwitchHelixClient.clear()
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            super.setup()
        }

        @Test
        fun noAccount_earlyReturnAsSuccess() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = false
            traceRule.isTraceable = false
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetMe_returnAsFailure() = testScope.runTest {
            // setup
            traceRule.isTraceable = false
            FakeTwitchHelixClient.hasAccount = true
            testDispatcher.add(ExpectedResponse.twitchMe(json = TwitchErrorJson.badRequest()))
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailure<TwitchException>()
            localSource.fetchMe().shouldBeSuccess { it shouldBe null }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun noFollowing_returnAsSuccess() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = emptyList()),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetFollowing_returnFailure() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, json = TwitchErrorJson.badRequest()),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailure<TwitchException>()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun has1FollowingWithStream_returnAsSuccess() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            val category = category("1")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = listOf(stream("1", category, userDetail))),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(userId = userDetail.id, json = TwitchErrorJson.notFound()),
                ExpectedResponse.twitchUsers(listOf(userDetail)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data shouldHaveSize 1 }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun has1FollowingWithSchedule_returnAsSuccess() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            val category = category("1")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(
                    data = schedule(
                        streamSchedule = listOf(streamSchedule("1", category)),
                        broadcaster = broadcaster(userDetail),
                    ),
                ),
                ExpectedResponse.twitchGame(listOf(category)),
                ExpectedResponse.twitchUsers(listOf(userDetail)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data shouldHaveSize 1 }
        }

        @Test
        fun has1FollowingWithSchedule_failedToGetCategory_returnAsSuccess() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            val category = category("1")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(
                    userId = userDetail.id,
                    data = schedule(
                        streamSchedule = listOf(streamSchedule("1", category)),
                        broadcaster = broadcaster(userDetail),
                    ),
                ),
                ExpectedResponse.twitchGame(query = listOf(category.id), json = TwitchErrorJson.badRequest()),
                ExpectedResponse.twitchUsers(users = listOf(userDetail)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data shouldHaveSize 1 }
        }

        @Test
        fun has1FollowingWithSchedule_failedToGetSchedule_returnAsFailure() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(userId = userDetail.id, json = TwitchErrorJson.badRequest()),
                ExpectedResponse.twitchUsers(users = listOf(userDetail)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailure()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetUserDetail_returnAsFailure() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            val category = category("1")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = listOf(stream("1", category, userDetail))),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(userId = userDetail.id, json = TwitchErrorJson.notFound()),
                ExpectedResponse.twitchUsers(query = listOf(userDetail.id), json = TwitchErrorJson.badRequest()),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailure<TwitchException>()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }

        @Test
        fun failedToGetStreams_returnAsFailure() = testScope.runTest {
            // setup
            FakeTwitchHelixClient.hasAccount = true
            val userDetail = userDetail("10")
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(meId = me.id, json = TwitchErrorJson.badRequest()),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = listOf(broadcaster(userDetail))),
                ExpectedResponse.twitchChannelSchedule(userId = userDetail.id, json = TwitchErrorJson.notFound()),
                ExpectedResponse.twitchUsers(users = listOf(userDetail)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeFailure()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }
    }

    @HiltAndroidTest
    class HasItems : Base() {
        private val current = Instant.parse("2025-04-29T00:00:00Z")
        private val category = category("1")
        private val streamUser = userDetail("10")
        private val scheduleUser = userDetail("11")
        private val streamSchedule = streamSchedule("1", category, Instant.parse("2025-04-30T00:00:00Z"))

        @Before
        override fun setup(): Unit = runBlocking {
            super.setup()
            val followings = listOf(streamUser, scheduleUser).map { broadcaster(it) }
            FakeTwitchHelixClient.hasAccount = true
            testDispatcher.add(
                ExpectedResponse.twitchMe(me),
                ExpectedResponse.twitchStreamsFollowed(
                    meId = me.id,
                    data = listOf(stream("1", category("2"), streamUser)),
                ),
                ExpectedResponse.twitchGame(data = listOf(category)),
                ExpectedResponse.twitchChannelsFollowed(meId = me.id, users = followings),
                ExpectedResponse.twitchChannelSchedule(schedule(listOf(streamSchedule), broadcaster(scheduleUser))),
                ExpectedResponse.twitchChannelSchedule(schedule(emptyList(), broadcaster(streamUser))),
                ExpectedResponse.twitchUsers(listOf(streamUser, scheduleUser)),
            )
            FakeDateTimeProviderModule.instant = current
        }

        @After
        fun tearDown() {
            FakeTwitchHelixClient.clear()
            FakeDateTimeProviderModule.clear()
        }

        private suspend fun TestScope.initialLoad() {
            val actual = sut.invoke()
            advanceUntilIdle()
            actual.shouldBeSuccess()
            localSource.fetchMe().shouldBeSuccess { it.shouldNotBeNull() }
        }

        @Test
        fun hasOnAirAndUpcomingItems() = testScope.runTest {
            // setup
            initialLoad()
            FakeDateTimeProviderModule.instant = current + Duration.ofMinutes(3)
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data shouldHaveSize 1 }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data shouldHaveSize 1 }
        }

        @Test
        fun onAirStreamIsFinished_onAirIsEmpty() = testScope.runTest {
            // setup
            initialLoad()
            val now = current + Duration.ofMinutes(10)
            FakeDateTimeProviderModule.instant = now
            testDispatcher.add(ExpectedResponse.twitchStreamsFollowed(meId = me.id, data = emptyList()))
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data.shouldBeEmpty() }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data shouldHaveSize 1 }
        }

        @Test
        fun upcomingPublishDeadlineIsOver_upcomingIsEmpty() = testScope.runTest {
            // setup
            initialLoad()
            FakeDateTimeProviderModule.instant = streamSchedule.startTime + Duration.ofHours(7)
            testDispatcher.add(
                ExpectedResponse.twitchChannelSchedule(userId = scheduleUser.id, json = TwitchErrorJson.notFound()),
                ExpectedResponse.twitchUsers(listOf(streamUser)),
            )
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            actual.shouldBeSuccess()
            livePagingSource.onAir.testWithRefresh { data shouldHaveSize 1 }
            livePagingSource.upcoming(dateTimeProvider.now()).testWithRefresh { data.shouldBeEmpty() }
        }
    }

    abstract class Base {
        @get:Rule(order = 0)
        val hilt = HiltAndroidRule(this)

        @get:Rule(order = 1)
        val testScope = TestCoroutineScopeRule()

        @get:Rule(order = 2)
        val traceRule = AppTraceVerifier()

        @get:Rule(order = 3)
        val server = MockServerRule { TwitchHelixClientModule.baseUrl = it.toString() }
        val testDispatcher = TestDispatcher.create()

        @Inject
        internal lateinit var sut: FetchTwitchStreamUseCase

        @Inject
        internal lateinit var localSource: TwitchDataSource.Local

        @Inject
        internal lateinit var extendedSource: TwitchDataSource.Extended

        @Inject
        internal lateinit var livePagingSource: LiveDataPagingSource

        @Inject
        internal lateinit var dateTimeProvider: DateTimeProvider

        @Before
        open fun setup() = runBlocking {
            hilt.inject()
            extendedSource.deleteAllTables()
            server.setClient(testDispatcher)
        }
    }
}

private val me = TwitchUserJson(TwitchUser.Id("1"), "user_me")

private fun userDetail(id: String, name: String = "user_$id"): TwitchUserJson =
    TwitchUserJson(TwitchUser.Id(id), name)

private fun broadcaster(user: TwitchUser): TwitchFollowingJson = TwitchFollowingJson(user)

private fun category(id: String): TwitchGameJson = TwitchGameJson(TwitchCategory.Id(id), "name")

private fun stream(
    id: String = "1",
    category: TwitchGameJson,
    userDetail: TwitchUserJson,
): TwitchFollowedStreamJson = TwitchFollowedStreamJson(id, userDetail, category)

private fun streamSchedule(
    id: String,
    category: TwitchGameJson,
    startTime: Instant = Instant.EPOCH,
): TwitchScheduleJson = TwitchScheduleJson(id, category, startTime)

private fun schedule(
    streamSchedule: List<TwitchScheduleJson>,
    broadcaster: TwitchFollowingJson,
    vacation: TwitchChannelSchedule.Vacation? = null,
): TwitchChannelScheduleJson = TwitchChannelScheduleJson(streamSchedule, broadcaster, vacation)

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TwitchHelixClientModule::class],
)
interface FakeTwitchHelixClient {
    companion object {
        var hasAccount: Boolean? = null

        @Provides
        @Singleton
        @LivePlatformQualifier(Twitch::class)
        fun provideAccountRepository(): AccountRepository = object : AccountRepository {
            override fun hasAccount(): Boolean = checkNotNull(hasAccount)
            override val isTokenInvalid: Flow<Boolean?> get() = emptyFlow()
        }

        fun clear() {
            hasAccount = null
        }
    }
}

interface FakeDateTimeProviderImpl : FakeDateTimeProviderModule
interface InMemoryDbModuleImpl : InMemoryDbModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
