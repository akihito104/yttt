package com.freshdigitable.yttt.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.di.DbModule
import com.freshdigitable.yttt.data.source.remote.Broadcaster
import com.freshdigitable.yttt.data.source.remote.ChannelStreamScheduleResponse
import com.freshdigitable.yttt.data.source.remote.FollowedChannelsResponse
import com.freshdigitable.yttt.data.source.remote.FollowingStream
import com.freshdigitable.yttt.data.source.remote.FollowingStreamsResponse
import com.freshdigitable.yttt.data.source.remote.Pagination
import com.freshdigitable.yttt.data.source.remote.TwitchGameResponse
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchUserDetailRemote
import com.freshdigitable.yttt.data.source.remote.TwitchUserResponse
import com.freshdigitable.yttt.data.source.remote.TwitchVideosResponse
import com.freshdigitable.yttt.di.CoroutineModule
import com.freshdigitable.yttt.di.DateTimeModule
import com.freshdigitable.yttt.di.TwitchModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidTest
class TwitchRemoteMediatorTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var localSource: TwitchDataSource.Local

    @Inject
    internal lateinit var pagerFactory: TwitchSubscriptionPagerFactory

    @Inject
    lateinit var db: AppDatabase
    private val broadcaster = broadcaster(100)
    private val followings =
        TwitchFollowings.createAtFetched(authUser.id, broadcaster, Instant.ofEpochMilli(20))

    private suspend fun setup() {
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(10)
        localSource.setMe(authUser)
        val stream = broadcaster.take(10).map { stream(it) }
        val streams = object : TwitchStreams.Updated {
            override val followerId: TwitchUser.Id get() = authUser.id
            override val streams: List<TwitchStream> get() = stream
            override val updatableAt: Instant get() = Instant.EPOCH
            override val updatableThumbnails: Set<String> get() = emptySet()
            override val deletedThumbnails: Set<String> get() = emptySet()
        }
        localSource.replaceFollowedStreams(streams)
        localSource.replaceAllFollowings(followings)
        FakeRemoteSourceModule.userDetails = broadcaster.map { it.toUserDetail() }
    }

    @After
    fun tearDown() = runTest {
        FakeDateTimeProviderModule.instant = null
        FakeRemoteSourceModule.userDetails = emptyList()
        db.close()
    }

    private val sut: Pager<Int, LiveSubscription> by lazy {
        pagerFactory.create(config = PagingConfig(pageSize = 20))
    }

    @Test
    fun firstTimeToLoadSubscriptionPage() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        setup()
        FakeDateTimeProviderModule.instant = followings.updatableAt.minusMillis(1)
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(60) // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
            .allMatch { it.channel.iconUrl.isNotEmpty() }
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_needsRefresh() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        setup()
        val base = followings.updatableAt
        FakeDateTimeProviderModule.instant = base
        FakeRemoteSourceModule.broadcasters = broadcaster(100).toTypedArray()
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(60) // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
            .allMatch { it.channel.iconUrl.isNotEmpty() }
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_scrollToLastItem() = runTest {
        // setup
        TestCoroutineScopeModule.testScheduler = testScheduler
        hiltRule.inject()
        setup()
        FakeDateTimeProviderModule.instant = followings.updatableAt.minusMillis(1)
        // exercise
        val actual = sut.flow.asSnapshot {
            appendScrollWhile { it.channel.id.value != "user99" } // footer
        }
        // verify
        assertThat(actual).isNotEmpty()
            .allMatch { it.channel.iconUrl.isNotEmpty() }
    }

    private companion object {
        val authUser = TwitchUserDetailRemote(
            id = TwitchUser.Id("user.me"),
            loginName = "user.me",
            displayName = "user.me",
            description = "description",
            createdAt = Instant.EPOCH,
            profileImageUrl = "",
        )

        fun broadcaster(count: Int): List<Broadcaster> = (0..<count).map {
            Broadcaster(
                id = TwitchUser.Id("user$it"),
                followedAt = Instant.EPOCH,
                displayName = "user$it",
                loginName = "user$it",
            )
        }

        fun stream(broadcaster: Broadcaster): FollowingStream = FollowingStream(
            id = TwitchStream.Id("stream_${broadcaster.id.value}"),
            userId = broadcaster.id.value,
            loginName = broadcaster.loginName,
            displayName = broadcaster.displayName,
            gameId = TwitchCategory.Id("game"),
            gameName = "gameName",
            type = "type",
            title = "title",
            startedAt = Instant.EPOCH,
            viewCount = 100,
            thumbnailUrlBase = "",
            tags = emptyList(),
            isMature = false,
            language = "ja",
        )

        fun Broadcaster.toUserDetail() = TwitchUserDetailRemote(
            id = id,
            loginName = loginName,
            displayName = displayName,
            description = "",
            createdAt = Instant.EPOCH,
            profileImageUrl = "<icon:${id.value} url is here>",
        )
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DateTimeModule::class],
)
interface FakeDateTimeProviderModule {
    companion object {
        var instant: Instant? = null

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
    replaces = [TwitchModule::class],
)
interface FakeRemoteSourceModule {
    companion object {
        internal var userDetails: List<TwitchUserDetailRemote> = emptyList()
        internal var broadcasters: Array<Broadcaster>? = null

        @Provides
        internal fun provideHelixService(): TwitchHelixService {
            return object : TwitchHelixService {
                override fun getUser(
                    id: Collection<TwitchUser.Id>?,
                    loginName: Collection<String>?
                ): Call<TwitchUserResponse> =
                    FakeCall(Response.success(TwitchUserResponse(userDetails)))

                override fun getFollowing(
                    userId: TwitchUser.Id,
                    broadcasterId: TwitchUser.Id?,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<FollowedChannelsResponse> = FakeCall(
                    Response.success(
                        FollowedChannelsResponse(
                            data = broadcasters!!,
                            pagination = Pagination(null),
                            total = broadcasters!!.size,
                        )
                    )
                )

                override fun getFollowedStreams(
                    userId: TwitchUser.Id,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<FollowingStreamsResponse> {
                    TODO("Not yet implemented")
                }

                override fun getChannelStreamSchedule(
                    broadcasterId: TwitchUser.Id,
                    segmentId: TwitchChannelSchedule.Stream.Id?,
                    startTime: Instant?,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<ChannelStreamScheduleResponse> {
                    TODO("Not yet implemented")
                }

                override fun getVideoByUserId(
                    userId: TwitchUser.Id,
                    language: String?,
                    period: String?,
                    sort: String?,
                    type: String?,
                    itemsPerPage: Int?,
                    nextCursor: String?,
                    prevCursor: String?
                ): Call<TwitchVideosResponse> {
                    TODO("Not yet implemented")
                }

                override fun getGame(id: Set<TwitchCategory.Id>): Call<TwitchGameResponse> {
                    TODO("Not yet implemented")
                }
            }
        }
    }
}

private class FakeCall<T>(private val response: Response<T>) : Call<T> {
    override fun execute(): Response<T> = response

    override fun clone(): Call<T> {
        TODO("Not yet implemented")
    }

    override fun isExecuted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun cancel() {
        TODO("Not yet implemented")
    }

    override fun isCanceled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun request(): Request {
        TODO("Not yet implemented")
    }

    override fun timeout(): Timeout {
        TODO("Not yet implemented")
    }

    override fun enqueue(p0: Callback<T>) {
        TODO("Not yet implemented")
    }
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
