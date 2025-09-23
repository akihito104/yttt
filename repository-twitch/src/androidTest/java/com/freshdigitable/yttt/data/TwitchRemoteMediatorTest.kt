package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchLiveSubscription
import com.freshdigitable.yttt.data.source.remote.Broadcaster
import com.freshdigitable.yttt.data.source.remote.ChannelStreamScheduleResponse
import com.freshdigitable.yttt.data.source.remote.FollowedChannelsResponse
import com.freshdigitable.yttt.data.source.remote.FollowingStream
import com.freshdigitable.yttt.data.source.remote.FollowingStreamsResponse
import com.freshdigitable.yttt.data.source.remote.Pagination
import com.freshdigitable.yttt.data.source.remote.TwitchException
import com.freshdigitable.yttt.data.source.remote.TwitchGameResponse
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchUserDetailRemote
import com.freshdigitable.yttt.data.source.remote.TwitchUserResponse
import com.freshdigitable.yttt.data.source.remote.TwitchVideosResponse
import com.freshdigitable.yttt.di.TwitchModule
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.shouldBeError
import com.freshdigitable.yttt.test.shouldBeSuccess
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.test.advanceUntilIdle
import okhttp3.Headers
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class TwitchRemoteMediatorTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var localSource: TwitchDataSource.Local

    @Inject
    lateinit var extendedSource: TwitchDataSource.Extended

    @Inject
    internal lateinit var pagerFactory: TwitchSubscriptionPagerFactory

    private val broadcaster = broadcaster(100)
    private val fetchedAt = Instant.ofEpochMilli(20)
    private val maxAge = Duration.ofMinutes(5)
    private val updatableAt = fetchedAt + maxAge
    private val followings =
        TwitchFollowings.create(authUser.id, broadcaster, CacheControl.create(fetchedAt, maxAge))

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule(
        setup = {
            hiltRule.inject()
            extendedSource.deleteAllTables()

            FakeDateTimeProviderModule.apply {
                onTimeAdvanced = { current ->
                    FakeRemoteSourceModule.userDetails = {
                        Response.success(
                            TwitchUserResponse(broadcaster.map { it.toUserDetail() }),
                            Headers.Builder().add("date", current).build(),
                        )
                    }
                }
                instant = Instant.ofEpochMilli(100)
            }
            localSource.setMe(authUser.toUpdatable(CacheControl.fromRemote(Instant.EPOCH)))
            val stream = broadcaster.take(10).map { stream(it) }
            val streams = object : TwitchStreams.Updated {
                override val followerId: TwitchUser.Id get() = authUser.id
                override val streams: List<TwitchStream> get() = stream
                override val updatableThumbnails: Set<String> get() = emptySet()
                override val deletedThumbnails: Set<String> get() = emptySet()
            }
            extendedSource.replaceFollowedStreams(streams.toUpdatable())
            localSource.replaceAllFollowings(followings)
        },
    )

    @After
    fun tearDown() {
        FakeDateTimeProviderModule.clear()
        FakeRemoteSourceModule.clear()
    }

    private val sut: Pager<Int, LiveSubscription> by lazy {
        pagerFactory.create(config = PagingConfig(pageSize = 20))
    }

    @Test
    fun firstTimeToLoadSubscriptionPage() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt.minusMillis(1)
        // exercise
        val actual = sut.flow.asSnapshot()
        advanceUntilIdle()
        // verify
        actual shouldHaveSize 60 // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
        actual.map { it.channel.iconUrl } shouldNotContain ""
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_needsRefresh() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt
        FakeRemoteSourceModule.following = { followingResponse(100, updatableAt) }
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        actual shouldHaveSize 60 // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
        actual.map { it.channel.iconUrl } shouldNotContain ""
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_scrollToLastItem() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt.minusMillis(1)
        // exercise
        val actual = sut.flow.asSnapshot {
            appendScrollWhile { it.channel.id.value != "user99" } // footer
        }
        // verify
        actual.shouldNotBeEmpty()
        actual.map { it.channel.iconUrl } shouldNotContain ""
    }

    @Inject
    internal lateinit var remoteMediator: TwitchRemoteMediator

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun failedToGetFollowingsAtRefresh_returnsError() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt
        FakeRemoteSourceModule.following =
            { Response.error(500, "internal error".toResponseBody()) }
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), null, PagingConfig(20), 0),
        )
        // verify
        actual.shouldBeError<TwitchException>()
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun failedToGetUserDetailAtRefresh_returnsSuccess() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt
        FakeRemoteSourceModule.following = { followingResponse(100, updatableAt) }
        FakeRemoteSourceModule.userDetails =
            { Response.error(500, "internal error".toResponseBody()) }
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), null, PagingConfig(20), 0),
        )
        // verify
        actual.shouldBeSuccess(endOfPaginationReached = true)
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun failedToGetFollowingsAtPrepend_returnsSuccess() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt.minusMillis(1)
        FakeRemoteSourceModule.userDetails =
            { Response.error(500, "internal error".toResponseBody()) }
        // exercise
        val actual = remoteMediator.load(
            LoadType.PREPEND,
            PagingState(
                listOf(liveSubscriptionPage(broadcaster.take(60).map { liveSubscription(it) })),
                null,
                PagingConfig(20),
                0,
            ),
        )
        // verify
        actual.shouldBeSuccess(endOfPaginationReached = true)
    }

    private companion object {
        val authUser = object : TwitchUserDetail by TwitchUserDetailRemote(
            id = TwitchUser.Id("user.me"),
            loginName = "user.me",
            displayName = "user.me",
            description = "description",
            createdAt = Instant.EPOCH,
            profileImageUrl = "",
        ) {}

        fun broadcaster(count: Int): List<Broadcaster> = (0..<count).map {
            Broadcaster(
                id = TwitchUser.Id("user$it"),
                followedAt = Instant.EPOCH,
                displayName = "user$it",
                loginName = "user$it",
            )
        }

        fun followingResponse(count: Int, date: Instant): Response<FollowedChannelsResponse> =
            Response.success(
                FollowedChannelsResponse(
                    item = broadcaster(count),
                    pagination = Pagination(null),
                    total = count,
                ),
                Headers.Builder()
                    .add("date", date)
                    .build(),
            )

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

        fun liveSubscription(
            broadcaster: Broadcaster,
            channelIconUrl: String? = null,
        ): TwitchLiveSubscription = TwitchLiveSubscription(
            idValue = authUser.id,
            subscribeSince = Instant.EPOCH,
            channelId = broadcaster.id,
            channelName = broadcaster.displayName,
            channelIconUrl = channelIconUrl,
        )

        fun liveSubscriptionPage(
            liveSubscriptions: List<TwitchLiveSubscription>,
            prevKey: Int? = null,
            nextKey: Int? = null,
        ): PagingSource.LoadResult.Page<Int, LiveSubscription> =
            PagingSource.LoadResult.Page(liveSubscriptions, prevKey, nextKey)
    }
}

fun FakeRemoteSourceModule.Companion.clear() {
    userDetails = null
    following = null
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TwitchModule::class],
)
interface FakeRemoteSourceModule {
    companion object {
        internal var userDetails: (() -> Response<TwitchUserResponse>)? = null
        internal var following: (() -> Response<FollowedChannelsResponse>)? = null

        @Provides
        internal fun provideHelixService(): TwitchHelixService {
            return object : TwitchHelixService {
                override fun getUser(
                    id: Collection<TwitchUser.Id>?,
                    loginName: Collection<String>?,
                ): Call<TwitchUserResponse> = FakeCall(userDetails!!.invoke())

                override fun getFollowing(
                    userId: TwitchUser.Id,
                    broadcasterId: TwitchUser.Id?,
                    itemsPerPage: Int?,
                    cursor: String?,
                ): Call<FollowedChannelsResponse> = FakeCall(following!!.invoke())

                override fun getFollowedStreams(
                    userId: TwitchUser.Id,
                    itemsPerPage: Int?,
                    cursor: String?,
                ): Call<FollowingStreamsResponse> = throw NotImplementedError()

                override fun getChannelStreamSchedule(
                    broadcasterId: TwitchUser.Id,
                    segmentId: TwitchChannelSchedule.Stream.Id?,
                    startTime: Instant?,
                    itemsPerPage: Int?,
                    cursor: String?,
                ): Call<ChannelStreamScheduleResponse> = throw NotImplementedError()

                override fun getVideoByUserId(
                    userId: TwitchUser.Id,
                    language: String?,
                    period: String?,
                    sort: String?,
                    type: String?,
                    itemsPerPage: Int?,
                    nextCursor: String?,
                    prevCursor: String?,
                ): Call<TwitchVideosResponse> = throw NotImplementedError()

                override fun getGame(id: Set<TwitchCategory.Id>): Call<TwitchGameResponse> =
                    throw NotImplementedError()
            }
        }
    }
}

private class FakeCall<T>(private val response: Response<T>) : Call<T> {
    override fun execute(): Response<T> = response

    override fun clone(): Call<T> = throw NotImplementedError()
    override fun isExecuted(): Boolean = throw NotImplementedError()
    override fun cancel() = throw NotImplementedError()
    override fun isCanceled(): Boolean = throw NotImplementedError()
    override fun request(): Request = throw NotImplementedError()
    override fun timeout(): Timeout = throw NotImplementedError()
    override fun enqueue(p0: Callback<T>) = throw NotImplementedError()
}

interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
