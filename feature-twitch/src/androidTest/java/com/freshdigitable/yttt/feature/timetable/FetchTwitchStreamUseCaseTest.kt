package com.freshdigitable.yttt.feature.timetable

import app.cash.turbine.test
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchException
import com.freshdigitable.yttt.data.source.remote.TwitchHelixClient
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.di.TwitchHelixClientModule
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.AppTraceVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
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
    class Init {
        @get:Rule(order = 0)
        val hilt = HiltAndroidRule(this)

        @get:Rule(order = 1)
        val testScope = TestCoroutineScopeRule()

        @get:Rule(order = 2)
        val traceRule = AppTraceVerifier()

        @Inject
        internal lateinit var sut: FetchTwitchStreamUseCase

        @Inject
        internal lateinit var localSource: TwitchDataSource.Local

        @Before
        fun setup() {
            FakeTwitchHelixClient.clear()
            FakeDateTimeProviderModule.instant = null
            hilt.inject()
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
            assertResultThat(actual).isSuccess()
            localSource.onAir.test { assertThat(awaitItem()).isEmpty() }
            localSource.upcoming.test { assertThat(awaitItem()).isEmpty() }
        }

        @Test
        fun failedToGetMe_returnAsFailure() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true
                meResponse = { throw TwitchException(400, "Bad request.") }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isFailure {
                it.isInstanceOf(TwitchException::class.java)
            }
            assertResultThat(localSource.fetchMe()).value().isNull()
            localSource.onAir.test { assertThat(awaitItem()).isEmpty() }
            localSource.upcoming.test { assertThat(awaitItem()).isEmpty() }
        }

        @Test
        fun noFollowing_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                followingsResponse = { NetworkResponse.create(item = emptyList()) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun failedToGetFollowing_returnFailure() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                followingsResponse = { throw TwitchException(400, "Bad request.") }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isFailure {
                it.isInstanceOf(TwitchException::class.java)
            }
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test { assertThat(awaitItem()).isEmpty() }
            localSource.upcoming.test { assertThat(awaitItem()).isEmpty() }
        }

        @Test
        fun has1FollowingWithStream_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                val category = category("1")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = {
                    NetworkResponse.create(item = listOf(stream("1", category, userDetail)))
                }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = { throw TwitchException(404, "Not found") }
                userResponse = { NetworkResponse.create(item = listOf(userDetail)) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).hasSize(1)
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun has1FollowingWithSchedule_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                val category = category("1")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = {
                    NetworkResponse.create(
                        item = schedule(
                            streamSchedule = listOf(streamSchedule("1", category)),
                            broadcaster = broadcaster(userDetail),
                        ),
                    )
                }
                categoryResponse = { NetworkResponse.create(item = listOf(category)) }
                userResponse = { NetworkResponse.create(item = listOf(userDetail)) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).hasSize(1)
            }
        }

        @Test
        fun has1FollowingWithSchedule_failedToGetCategory_returnAsSuccess() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = {
                    NetworkResponse.create(
                        item = schedule(
                            streamSchedule = listOf(streamSchedule("1", category("1"))),
                            broadcaster = broadcaster(userDetail),
                        ),
                    )
                }
                categoryResponse = { throw TwitchException(400, "Bad request.") }
                userResponse = { NetworkResponse.create(item = listOf(userDetail)) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).hasSize(1)
            }
        }

        @Test
        fun has1FollowingWithSchedule_failedToGetSchedule_returnAsFailure() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = { throw TwitchException(400, "Bad request.") }
                userResponse = { NetworkResponse.create(item = listOf(userDetail)) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isFailure()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun failedToGetUserDetail_returnAsFailure() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                val category = category("1")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = {
                    NetworkResponse.create(item = listOf(stream("1", category, userDetail)))
                }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = { throw TwitchException(404, "Not found") }
                userResponse = { throw TwitchException(400, "Bad request") }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isFailure {
                it.isInstanceOf(TwitchException::class.java)
            }
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

        @Test
        fun failedToGetStreams_returnAsFailure() = testScope.runTest {
            // setup
            FakeDateTimeProviderModule.instant = Instant.EPOCH
            FakeTwitchHelixClient.apply {
                hasAccount = true

                val userDetail = userDetail("10")
                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = { throw TwitchException(400, "Bad request.") }
                followingsResponse =
                    { NetworkResponse.create(item = listOf(broadcaster(userDetail))) }
                scheduleResponse = { throw TwitchException(404, "Not found") }
                userResponse = { NetworkResponse.create(item = listOf(userDetail)) }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isFailure()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
            localSource.onAir.test { assertThat(awaitItem()).isEmpty() }
            localSource.upcoming.test { assertThat(awaitItem()).isEmpty() }
        }
    }

    @HiltAndroidTest
    class HasItems {
        @get:Rule(order = 0)
        val hilt = HiltAndroidRule(this)

        @get:Rule(order = 1)
        val testScope = TestCoroutineScopeRule()

        @get:Rule(order = 2)
        val rule = AppTraceVerifier()

        @Inject
        internal lateinit var sut: FetchTwitchStreamUseCase

        @Inject
        internal lateinit var localSource: TwitchDataSource.Local

        private val current = Instant.parse("2025-04-29T00:00:00Z")
        private val category = category("1")
        private val streamUser = userDetail("10")
        private val scheduleUser = userDetail("11")
        private val streamSchedule =
            streamSchedule("1", category, Instant.parse("2025-04-30T00:00:00Z"))

        @Before
        fun setup(): Unit = runBlocking {
            hilt.inject()
            localSource.deleteAllTables()

            FakeDateTimeProviderModule.instant = current
            val followings = listOf(streamUser, scheduleUser).map { broadcaster(it) }
            FakeTwitchHelixClient.apply {
                hasAccount = true

                meResponse = { NetworkResponse.create(item = me) }
                streamResponse = {
                    NetworkResponse.create(item = listOf(stream("1", category("2"), streamUser)))
                }
                categoryResponse = { NetworkResponse.create(item = listOf(category)) }
                followingsResponse = { NetworkResponse.create(item = followings) }
                scheduleResponse = {
                    NetworkResponse.create(
                        item = schedule(listOf(streamSchedule), broadcaster(scheduleUser))
                    )
                }
                userResponse = { NetworkResponse.create(item = listOf(streamUser, scheduleUser)) }
            }
        }

        @After
        fun tearDown() {
            FakeTwitchHelixClient.clear()
            FakeDateTimeProviderModule.instant = null
        }

        private suspend fun TestScope.initialLoad() {
            val actual = sut.invoke()
            advanceUntilIdle()
            assertResultThat(actual).isSuccess()
            assertResultThat(localSource.fetchMe()).apply {
                isSuccess()
                value().isNotNull()
            }
        }

        @Test
        fun hasOnAirAndUpcomingItems() = testScope.runTest {
            // setup
            initialLoad()
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            localSource.onAir.test {
                assertThat(awaitItem()).hasSize(1)
            }
            localSource.upcoming.test {
                val item = awaitItem()
                assertThat(item).hasSize(1)
                assertThat(item.first().schedule.category?.id).isEqualTo(category.id)
            }
        }

        @Test
        fun onAirStreamIsFinished_onAirIsEmpty() = testScope.runTest {
            // setup
            initialLoad()
            FakeDateTimeProviderModule.instant = current + Duration.ofMinutes(10)
            FakeTwitchHelixClient.apply {
                streamResponse = { NetworkResponse.create(item = emptyList()) }
                meResponse = null
                categoryResponse = null
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            localSource.onAir.test {
                assertThat(awaitItem()).isEmpty()
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).hasSize(1)
            }
        }

        @Test
        fun upcomingPublishDeadlineIsOver_upcomingIsEmpty() = testScope.runTest {
            // setup
            initialLoad()
            FakeDateTimeProviderModule.instant = streamSchedule.startTime + Duration.ofHours(7)
            FakeTwitchHelixClient.apply {
                meResponse = null
                categoryResponse = null
                scheduleResponse = { throw TwitchException(404, "Not found") }
            }
            // exercise
            val actual = sut.invoke()
            advanceUntilIdle()
            // verify
            assertResultThat(actual).isSuccess()
            localSource.onAir.test {
                assertThat(awaitItem()).hasSize(1)
            }
            localSource.upcoming.test {
                assertThat(awaitItem()).isEmpty()
            }
        }
    }
}

private val me = userDetail("1", "me")

private fun userDetail(
    id: String, name: String = "user_$id",
) = object : TwitchUserDetail {
    override val id: TwitchUser.Id get() = TwitchUser.Id(id)
    override val loginName: String get() = name
    override val displayName: String get() = name
    override val description: String get() = ""
    override val profileImageUrl: String get() = ""
    override val createdAt: Instant get() = Instant.EPOCH
}

private fun broadcaster(user: TwitchUser, followedAt: Instant = Instant.EPOCH) =
    object : TwitchBroadcaster {
        override val id: TwitchUser.Id get() = user.id
        override val loginName: String get() = user.loginName
        override val displayName: String get() = user.displayName
        override val followedAt: Instant get() = followedAt
    }

private fun category(id: String): TwitchCategory = object : TwitchCategory {
    override val id: TwitchCategory.Id get() = TwitchCategory.Id(id)
    override val name: String get() = "name"
    override val artUrlBase: String get() = "<url is here>"
    override val igdbId: String get() = id
}

private fun stream(
    id: String,
    category: TwitchCategory,
    userDetail: TwitchUserDetail,
): TwitchStream = object : TwitchStream {
    override val id: TwitchStream.Id get() = TwitchStream.Id(id)
    override val gameId: TwitchCategory.Id get() = category.id
    override val gameName: String get() = category.name
    override val type: String get() = "type"
    override val startedAt: Instant get() = Instant.EPOCH
    override val tags: List<String> get() = emptyList()
    override val isMature: Boolean get() = false
    override val user: TwitchUser get() = userDetail
    override val title: String get() = ""
    override val thumbnailUrlBase: String get() = "<url is here>"
    override val viewCount: Int get() = 100
    override val language: String get() = "ja"
}

private fun streamSchedule(
    id: String,
    category: TwitchCategory,
    startTime: Instant = Instant.EPOCH,
): TwitchChannelSchedule.Stream = object : TwitchChannelSchedule.Stream {
    override val id: TwitchChannelSchedule.Stream.Id
        get() = TwitchChannelSchedule.Stream.Id(id)
    override val category: TwitchCategory
        get() = object : TwitchCategory { // as server response
            override val id: TwitchCategory.Id get() = category.id
            override val name: String get() = category.name
        }
    override val startTime: Instant get() = startTime
    override val endTime: Instant? get() = null
    override val title: String get() = "title"
    override val canceledUntil: String? get() = null
    override val isRecurring: Boolean get() = false
}

private fun schedule(
    streamSchedule: List<TwitchChannelSchedule.Stream>,
    broadcaster: TwitchBroadcaster,
    vacation: TwitchChannelSchedule.Vacation? = null,
): TwitchChannelSchedule = object : TwitchChannelSchedule {
    override val segments: List<TwitchChannelSchedule.Stream> get() = streamSchedule
    override val broadcaster: TwitchUser get() = broadcaster
    override val vacation: TwitchChannelSchedule.Vacation? get() = vacation
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TwitchHelixClientModule::class]
)
interface FakeTwitchHelixClient {
    companion object {
        var meResponse: (() -> NetworkResponse<TwitchUserDetail?>)? = null
        var followingsResponse: (() -> NetworkResponse<List<TwitchBroadcaster>>)? = null
        var scheduleResponse: ((TwitchUser.Id) -> NetworkResponse<TwitchChannelSchedule>)? =
            null
        var streamResponse: (() -> NetworkResponse<List<TwitchStream>>)? = null
        var categoryResponse: (() -> NetworkResponse<List<TwitchCategory>>)? = null
        var userResponse: (() -> NetworkResponse<List<TwitchUserDetail>>)? = null

        @Provides
        @Singleton
        fun provideClient(): TwitchHelixClient = object : TwitchHelixClient {
            override suspend fun getMe(): NetworkResponse<TwitchUserDetail?> =
                meResponse!!.invoke()

            override suspend fun getFollowing(
                userId: TwitchUser.Id,
                broadcasterId: TwitchUser.Id?,
                itemsPerPage: Int?,
                cursor: String?,
            ): NetworkResponse<List<TwitchBroadcaster>> = followingsResponse!!.invoke()

            override suspend fun getFollowedStreams(
                me: TwitchUser.Id,
                itemsPerPage: Int?,
                cursor: String?,
            ): NetworkResponse<List<TwitchStream>> = streamResponse!!.invoke()

            override suspend fun getChannelStreamSchedule(
                id: TwitchUser.Id,
                segmentId: TwitchChannelSchedule.Stream.Id?,
                itemsPerPage: Int?,
                cursor: String?,
            ): NetworkResponse<TwitchChannelSchedule> {
                logD { "getChannelStreamSchedule: $id, $segmentId, $itemsPerPage, $cursor" }
                return scheduleResponse!!.invoke(id)
            }

            override suspend fun getGame(id: Set<TwitchCategory.Id>): NetworkResponse<List<TwitchCategory>> {
                logD { "getGame: $id" }
                return categoryResponse!!.invoke()
            }

            override suspend fun getUser(ids: Set<TwitchUser.Id>?): NetworkResponse<List<TwitchUserDetail>> =
                userResponse!!.invoke()

            override suspend fun getVideoByUserId(
                id: TwitchUser.Id,
                itemCount: Int
            ): NetworkResponse<List<TwitchVideoDetail>> = throw NotImplementedError()
        }

        var hasAccount: Boolean? = null

        @Provides
        @Singleton
        @LivePlatformQualifier(Twitch::class)
        fun provideAccountRepository(): AccountRepository = object : AccountRepository {
            override fun hasAccount(): Boolean = checkNotNull(hasAccount)
            override val isTokenInvalid: Flow<Boolean?> get() = emptyFlow()
        }

        fun clear() {
            meResponse = null
            followingsResponse = null
            scheduleResponse = null
            categoryResponse = null
            streamResponse = null
            userResponse = null
            hasAccount = null
        }
    }
}

interface FakeDateTimeProviderImpl : FakeDateTimeProviderModule
interface InMemoryDbModuleImpl : InMemoryDbModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
