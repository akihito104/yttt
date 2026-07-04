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
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchLiveSubscription
import com.freshdigitable.yttt.data.source.remote.Broadcaster
import com.freshdigitable.yttt.data.source.remote.FollowingStream
import com.freshdigitable.yttt.data.source.remote.TwitchException
import com.freshdigitable.yttt.data.source.remote.TwitchUserDetailRemote
import com.freshdigitable.yttt.di.TwitchHelixClientModule
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerDispatcher.ExpectedResponse
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.ResponseJson
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.TwitchErrorJson
import com.freshdigitable.yttt.test.TwitchFollowingJson
import com.freshdigitable.yttt.test.TwitchUserJson
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.shouldBeError
import com.freshdigitable.yttt.test.shouldBeSuccess
import com.freshdigitable.yttt.test.twitchChannelsFollowed
import com.freshdigitable.yttt.test.twitchUsers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@HiltAndroidTest
class TwitchRemoteMediatorTest {
    @get:Rule(order = 0)
    val server = MockServerRule { TwitchHelixClientModule.baseUrl = it.toString() }

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var localSource: TwitchDataSource.Local

    @Inject
    lateinit var extendedSource: TwitchDataSource.Extended

    @Inject
    internal lateinit var pagerFactory: TwitchSubscriptionPagerFactory

    private val broadcaster = broadcaster(100)
    private val fetchedAt = Instant.fromEpochMilliseconds(20)
    private val maxAge = 5.minutes
    private val updatableAt = fetchedAt + maxAge
    private val followings =
        TwitchFollowings.create(authUser.id, broadcaster, CacheControl.create(fetchedAt, maxAge))

    @get:Rule(order = 2)
    val testScope = TestCoroutineScopeRule(
        setup = {
            hiltRule.inject()
            extendedSource.deleteAllTables()

            FakeDateTimeProviderModule.instant = Instant.fromEpochMilliseconds(100)
            localSource.setMe(authUser.toUpdatable(CacheControl.fromRemote(Instant.fromEpochMilliseconds(0))))
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
    }

    private val sut: Pager<Int, LiveSubscription> by lazy {
        pagerFactory.create(config = PagingConfig(pageSize = 20))
    }

    @Test
    fun firstTimeToLoadSubscriptionPage() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt - 1.milliseconds
        server.addResponses(broadcaster.take(60).toUserJson())
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
        server.addResponses(
            broadcaster.toFollowingJson(authUser.id),
            broadcaster.toUserJson(),
        )
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        actual shouldHaveSize 60 // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
        actual.map { it.channel.iconUrl } shouldNotContain ""
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_scrollToLastItem() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt - 1.milliseconds
        server.addResponses(
            broadcaster.take(60).toUserJson(),
            broadcaster.takeLast(40).toUserJson(),
        )
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
        server.addResponses(broadcaster.toFollowingJson(authUser.id, TwitchErrorJson.internalError()))
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
        server.addResponses(
            broadcaster.toFollowingJson(authUser.id),
            broadcaster.toUserJson(TwitchErrorJson.internalError()),
        )
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
        FakeDateTimeProviderModule.instant = updatableAt - 1.milliseconds
        val b = broadcaster.take(60)
        server.addResponses(b.toUserJson(TwitchErrorJson.internalError()))
        // exercise
        val actual = remoteMediator.load(
            LoadType.PREPEND,
            PagingState(
                listOf(liveSubscriptionPage(b.map { liveSubscription(it) })),
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
            createdAt = Instant.fromEpochMilliseconds(0),
            profileImageUrl = "",
        ) {}

        fun broadcaster(count: Int): List<Broadcaster> = (0..<count).map {
            Broadcaster(
                id = TwitchUser.Id("user$it"),
                followedAt = Instant.fromEpochMilliseconds(0),
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
            startedAt = Instant.fromEpochMilliseconds(0),
            viewCount = 100,
            thumbnailUrlBase = "",
            tags = emptyList(),
            isMature = false,
            language = "ja",
        )

        fun liveSubscription(
            broadcaster: Broadcaster,
            channelIconUrl: String? = null,
        ): TwitchLiveSubscription = TwitchLiveSubscription(
            idValue = authUser.id,
            subscribeSince = Instant.fromEpochMilliseconds(0),
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

private fun List<Broadcaster>.toUserJson(body: ResponseJson? = null): ExpectedResponse =
    ExpectedResponse.twitchUsers(
        users = map { TwitchUserJson(it.id, it.loginName, it.displayName) },
        json = body,
    )

private fun List<Broadcaster>.toFollowingJson(
    authUserId: TwitchUser.Id,
    body: ResponseJson? = null,
): ExpectedResponse = ExpectedResponse.twitchChannelsFollowed(
    meId = authUserId,
    users = map { TwitchFollowingJson(it) },
    json = body,
)

interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
