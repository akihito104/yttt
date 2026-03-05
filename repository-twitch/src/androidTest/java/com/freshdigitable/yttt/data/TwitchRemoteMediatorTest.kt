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
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.ResponseJson
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.TestDispatcher
import com.freshdigitable.yttt.test.TwitchErrorJson
import com.freshdigitable.yttt.test.TwitchFollowingJson
import com.freshdigitable.yttt.test.TwitchUserJson
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.shouldBeError
import com.freshdigitable.yttt.test.shouldBeSuccess
import com.freshdigitable.yttt.test.twitchResponse
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

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
    private val fetchedAt = Instant.ofEpochMilli(20)
    private val maxAge = Duration.ofMinutes(5)
    private val updatableAt = fetchedAt + maxAge
    private val followings =
        TwitchFollowings.create(authUser.id, broadcaster, CacheControl.create(fetchedAt, maxAge))

    private val dispatcher = TestDispatcherImpl(authUser.id)

    @get:Rule(order = 2)
    val testScope = TestCoroutineScopeRule(
        setup = {
            hiltRule.inject()
            extendedSource.deleteAllTables()

            FakeDateTimeProviderModule.instant = Instant.ofEpochMilli(100)
            server.setClient(dispatcher.dispatcher)
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
    }

    private val sut: Pager<Int, LiveSubscription> by lazy {
        pagerFactory.create(config = PagingConfig(pageSize = 20))
    }

    @Test
    fun firstTimeToLoadSubscriptionPage() = testScope.runTest {
        // setup
        FakeDateTimeProviderModule.instant = updatableAt.minusMillis(1)
        dispatcher.userDetail = { ids ->
            check(ids.toSet() == broadcaster.take(60).map { it.id }.toSet())
            broadcaster.map { TwitchUserJson(it.id) }.twitchResponse()
        }
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
        dispatcher.following = {
            broadcaster.map { TwitchFollowingJson(it) }.twitchResponse()
        }
        dispatcher.userDetail = { ids ->
            check(ids.toSet() == broadcaster.map { it.id }.toSet())
            broadcaster.map { TwitchUserJson(it.id) }.twitchResponse()
        }
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
        dispatcher.userDetail = { ids ->
            check(ids.toSet() == broadcaster.take(60).map { it.id }.toSet())
            broadcaster.map { TwitchUserJson(it.id) }.twitchResponse()
        }
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
        dispatcher.following = { TwitchErrorJson.internalError() }
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
        dispatcher.apply {
            following = { broadcaster.map { TwitchFollowingJson(it) }.twitchResponse() }
            userDetail = { ids ->
                check(ids.toSet() == broadcaster.map { it.id }.toSet())
                TwitchErrorJson.internalError()
            }
        }
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
        dispatcher.userDetail = { TwitchErrorJson.internalError() }
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

private class TestDispatcherImpl(
    private val authUserId: TwitchUser.Id,
    var userDetail: ((List<TwitchUser.Id>) -> ResponseJson)? = null,
    var following: (() -> ResponseJson)? = null,
) {
    val dispatcher = object : TestDispatcher {
        override fun dispatch(request: TestDispatcher.Request): ResponseJson {
            return when (request.encodedPath) {
                "/helix/users" -> {
                    val ids = request.queryParams("id").filterNotNull().map { TwitchUser.Id(it) }
                    check(ids.size <= 100)
                    userDetail!!(ids)
                }

                "/helix/channels/followed" -> {
                    check(request.queryParam("user_id") == authUserId.value)
                    check(request.queryParam("first")!!.toInt() <= 100)
                    following!!()
                }

                else -> throw AssertionError("unexpected path: ${request.encodedPath}")
            }
        }
    }
}

interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
