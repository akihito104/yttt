package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.MediatorResultSubject.Companion.assertThat
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
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
import com.freshdigitable.yttt.test.FakeYouTubeClient.Companion.updatable
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Before
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

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule()

    @Inject
    lateinit var localSource: TwitchDataSource.Local

    @Inject
    internal lateinit var pagerFactory: TwitchSubscriptionPagerFactory

    private val broadcaster = broadcaster(100)
    private val fetchedAt = Instant.ofEpochMilli(20)
    private val maxAge = Duration.ofMinutes(5)
    private val updatableAt = fetchedAt + maxAge
    private val followings =
        TwitchFollowings.create(authUser.id, broadcaster, CacheControl.create(fetchedAt, maxAge))

    @Before
    fun setup(): Unit = runBlocking {
        hiltRule.inject()
        FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(10)
        localSource.setMe(authUser.updatable())
        val stream = broadcaster.take(10).map { stream(it) }
        val streams = object : TwitchStreams.Updated {
            override val followerId: TwitchUser.Id get() = authUser.id
            override val streams: List<TwitchStream> get() = stream
            override val updatableThumbnails: Set<String> get() = emptySet()
            override val deletedThumbnails: Set<String> get() = emptySet()
        }
        localSource.replaceFollowedStreams(streams.updatable(maxAge = Duration.ZERO))
        localSource.replaceAllFollowings(followings)
        FakeRemoteSourceModule.userDetails =
            { Response.success(TwitchUserResponse(broadcaster.map { it.toUserDetail() })) }
    }

    @After
    fun tearDown() = runTest {
        FakeDateTimeProviderModule.instant = null
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
        assertThat(actual).hasSize(60) // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
        assertThat(actual.map { it.channel.iconUrl }).doesNotContain("")
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_needsRefresh() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt
        FakeRemoteSourceModule.following = { followingResponse(100) }
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(60) // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
        assertThat(actual.map { it.channel.iconUrl }).doesNotContain("")
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
        assertThat(actual).isNotEmpty()
        assertThat(actual.map { it.channel.iconUrl }).doesNotContain("")
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
        assertThat(actual).isError { throwable ->
            throwable.isInstanceOf(TwitchException::class.java)
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun failedToGetUserDetailAtRefresh_returnsSuccess() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt
        FakeRemoteSourceModule.following = { followingResponse(100) }
        FakeRemoteSourceModule.userDetails =
            { Response.error(500, "internal error".toResponseBody()) }
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), null, PagingConfig(20), 0),
        )
        // verify
        assertThat(actual).isSuccess(endOfPaginationReached = true)
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
        assertThat(actual).isSuccess(endOfPaginationReached = true)
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

        fun followingResponse(count: Int): Response<FollowedChannelsResponse> = Response.success(
            FollowedChannelsResponse(
                item = broadcaster(count),
                pagination = Pagination(null),
                total = count,
            )
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
            _id = authUser.id,
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
                    loginName: Collection<String>?
                ): Call<TwitchUserResponse> = FakeCall(userDetails!!.invoke())

                override fun getFollowing(
                    userId: TwitchUser.Id,
                    broadcasterId: TwitchUser.Id?,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<FollowedChannelsResponse> = FakeCall(following!!.invoke())

                override fun getFollowedStreams(
                    userId: TwitchUser.Id,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<FollowingStreamsResponse> = throw NotImplementedError()

                override fun getChannelStreamSchedule(
                    broadcasterId: TwitchUser.Id,
                    segmentId: TwitchChannelSchedule.Stream.Id?,
                    startTime: Instant?,
                    itemsPerPage: Int?,
                    cursor: String?
                ): Call<ChannelStreamScheduleResponse> = throw NotImplementedError()

                override fun getVideoByUserId(
                    userId: TwitchUser.Id,
                    language: String?,
                    period: String?,
                    sort: String?,
                    type: String?,
                    itemsPerPage: Int?,
                    nextCursor: String?,
                    prevCursor: String?
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

@OptIn(ExperimentalPagingApi::class)
class MediatorResultSubject(
    metadata: FailureMetadata,
    private val actual: RemoteMediator.MediatorResult?,
) : Subject(metadata, actual) {
    companion object {
        private fun factory(): Factory<MediatorResultSubject, RemoteMediator.MediatorResult> =
            Factory { metadata, actual -> MediatorResultSubject(metadata, actual) }

        fun assertThat(actual: RemoteMediator.MediatorResult?): MediatorResultSubject =
            Truth.assertAbout(factory()).that(actual)
    }

    fun isSuccess(endOfPaginationReached: Boolean) {
        check("MediatorResult.Success").that(actual)
            .isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        check("endOfPaginationReached").that((actual as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            .isEqualTo(endOfPaginationReached)
    }

    fun isError(throwableMatcher: (ThrowableSubject) -> Unit) {
        check("MediatorResult.Error").that(actual)
            .isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        throwableMatcher(throwable())
    }

    private fun throwable(): ThrowableSubject =
        check("throwable").that((actual as RemoteMediator.MediatorResult.Error).throwable)
}
