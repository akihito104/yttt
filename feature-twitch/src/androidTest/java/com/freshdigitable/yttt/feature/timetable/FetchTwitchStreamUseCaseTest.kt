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
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchException
import com.freshdigitable.yttt.data.source.remote.TwitchHelixClient
import com.freshdigitable.yttt.di.LivePlatformQualifier
import com.freshdigitable.yttt.di.TwitchHelixClientModule
import com.freshdigitable.yttt.feature.timetable.ResultSubject.Companion.assertResultThat
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class FetchTwitchStreamUseCaseTest {
    @get:Rule
    val rule = HiltAndroidRule(this)

    @Inject
    internal lateinit var sut: FetchTwitchStreamUseCase

    @Inject
    internal lateinit var localSource: TwitchDataSource.Local

    @Test
    fun noAccount_earlyReturn() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        rule.inject()
        FakeTwitchHelixClient.hasAccount = false
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        localSource.onAir.test {
            assertThat(awaitItem()).isEmpty()
        }
        localSource.upcoming.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun hasAccount_noFollowing_itemsAreEmpty() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        rule.inject()
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        FakeTwitchHelixClient.apply {
            hasAccount = true

            meResponse = TwitchHelixClient.Response.create(item = me)
            streamResponse = TwitchHelixClient.Response.create(item = emptyList())
            followingsResponse = TwitchHelixClient.Response.create(item = emptyList())
        }
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
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
    fun hasAccount_1Following_hasItems() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        rule.inject()
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        FakeTwitchHelixClient.apply {
            hasAccount = true

            val userDetail = userDetail("10")
            val category = category("1")
            meResponse = TwitchHelixClient.Response.create(item = me)
            streamResponse = TwitchHelixClient.Response.create(
                item = listOf(stream("1", category, userDetail)),
            )
            followingsResponse =
                TwitchHelixClient.Response.create(item = listOf(broadcaster(userDetail)))
            scheduleException = TwitchException("Not found", 404)
            userResponse = TwitchHelixClient.Response.create(item = listOf(userDetail))
        }
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
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
    fun hasAccount_1FollowingAndSchedule_hasItems() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        rule.inject()
        FakeDateTimeProviderModule.instant = Instant.EPOCH
        val category = category("1")
        FakeTwitchHelixClient.apply {
            hasAccount = true

            val streamUser = userDetail("10")
            val scheduleUser = userDetail("11")
            val followings = listOf(streamUser, scheduleUser).map { broadcaster(it) }
            meResponse = TwitchHelixClient.Response.create(item = me)
            streamResponse = TwitchHelixClient.Response.create(
                item = listOf(stream("1", category("2"), streamUser)),
            )
            categoryResponse = TwitchHelixClient.Response.create(item = listOf(category))
            followingsResponse = TwitchHelixClient.Response.create(item = followings)
            scheduleResponse = TwitchHelixClient.Response.create(
                item = schedule(listOf(streamSchedule("1", category)), broadcaster(scheduleUser))
            )
            userResponse =
                TwitchHelixClient.Response.create(item = listOf(streamUser, scheduleUser))
        }
        // exercise
        sut.invoke()
        advanceUntilIdle()
        // verify
        assertResultThat(localSource.fetchMe()).apply {
            isSuccess()
            value().isNotNull()
        }
        localSource.onAir.test {
            assertThat(awaitItem()).hasSize(1)
        }
        localSource.upcoming.test {
            val item = awaitItem()
            assertThat(item).hasSize(1)
            assertThat(item.first().schedule.category?.id).isEqualTo(category.id)
        }
    }
}

private fun <T> TwitchHelixClient.Response.Companion.create(
    item: T,
    nextPageToken: String? = null
) =
    object : TwitchHelixClient.Response<T> {
        override val item: T get() = item
        override val nextPageToken: String? get() = nextPageToken
    }

private val me = userDetail("1", "me")

private fun userDetail(id: String, name: String = "user_$id") = object : TwitchUserDetail {
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

private fun streamSchedule(id: String, category: TwitchCategory): TwitchChannelSchedule.Stream =
    object : TwitchChannelSchedule.Stream {
        override val id: TwitchChannelSchedule.Stream.Id
            get() = TwitchChannelSchedule.Stream.Id(id)
        override val startTime: Instant get() = Instant.EPOCH
        override val endTime: Instant? get() = null
        override val title: String get() = "title"
        override val canceledUntil: String? get() = null
        override val category: TwitchCategory get() = category
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
        var meResponse: TwitchHelixClient.Response<TwitchUserDetail?>? = null
        var followingsResponse: TwitchHelixClient.Response<List<TwitchBroadcaster>>? = null
        var scheduleResponse: TwitchHelixClient.Response<TwitchChannelSchedule>? = null
        var scheduleException: Throwable? = null
        var streamResponse: TwitchHelixClient.Response<List<TwitchStream>>? = null
        var categoryResponse: TwitchHelixClient.Response<List<TwitchCategory>>? = null
        var userResponse: TwitchHelixClient.Response<List<TwitchUserDetail>>? = null

        @Provides
        @Singleton
        fun provideClient(): TwitchHelixClient = object : TwitchHelixClient {
            override suspend fun getMe(): TwitchHelixClient.Response<TwitchUserDetail?> =
                meResponse!!

            override suspend fun getFollowing(
                userId: TwitchUser.Id,
                broadcasterId: TwitchUser.Id?,
                itemsPerPage: Int?,
                cursor: String?,
            ): TwitchHelixClient.Response<List<TwitchBroadcaster>> = followingsResponse!!

            override suspend fun getFollowedStreams(
                me: TwitchUser.Id,
                itemsPerPage: Int?,
                cursor: String?,
            ): TwitchHelixClient.Response<List<TwitchStream>> = streamResponse!!

            override suspend fun getChannelStreamSchedule(
                id: TwitchUser.Id,
                segmentId: TwitchChannelSchedule.Stream.Id?,
                itemsPerPage: Int?,
                cursor: String?,
            ): TwitchHelixClient.Response<TwitchChannelSchedule> {
                check(scheduleResponse != null || scheduleException != null)
                if (scheduleResponse != null) {
                    return scheduleResponse!!
                } else {
                    throw checkNotNull(scheduleException)
                }
            }

            override suspend fun getGame(id: Set<TwitchCategory.Id>): TwitchHelixClient.Response<List<TwitchCategory>> =
                categoryResponse!!

            override suspend fun getUser(ids: Set<TwitchUser.Id>?): TwitchHelixClient.Response<List<TwitchUserDetail>> =
                userResponse!!

            override suspend fun getVideoByUserId(
                id: TwitchUser.Id,
                itemCount: Int
            ): TwitchHelixClient.Response<List<TwitchVideoDetail>> = throw NotImplementedError()
        }

        var hasAccount: Boolean? = null

        @Provides
        @Singleton
        @LivePlatformQualifier(Twitch::class)
        fun provideAccountRepository(): AccountRepository = object : AccountRepository {
            override fun hasAccount(): Boolean = checkNotNull(hasAccount)
        }
    }
}

interface FakeDateTimeProviderImpl : FakeDateTimeProviderModule
interface InMemoryDbModuleImpl : InMemoryDbModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule

class ResultSubject<T>(
    metadata: FailureMetadata,
    private val actual: Result<T>?,
) : Subject(metadata, actual) {

    companion object {
        fun <T> factory(): Factory<ResultSubject<T>, Result<T>> =
            Factory { metadata, actual -> ResultSubject(metadata, actual) }

        fun <T> assertResultThat(actual: Result<T>): ResultSubject<T> =
            assertAbout(factory<T>()).that(actual)
    }

    fun isSuccess() {
        check("isSuccess").that(actual?.isSuccess).isTrue()
    }

    fun isFailure() {
        check("isFailure").that(actual?.isFailure).isTrue()
    }

    fun value(): Subject = check("value").that(actual?.getOrNull())
    fun throwable(): ThrowableSubject = check("throwable").that(actual?.exceptionOrNull())
}
