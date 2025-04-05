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
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.di.DbModule
import com.freshdigitable.yttt.data.source.remote.Broadcaster
import com.freshdigitable.yttt.data.source.remote.FollowingStream
import com.freshdigitable.yttt.data.source.remote.TwitchUserDetailRemote
import com.freshdigitable.yttt.di.DateTimeModule
import com.freshdigitable.yttt.di.TwitchModule
import com.freshdigitable.yttt.logD
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

    @Before
    fun setup() = runTest {
        hiltRule.inject()

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
        val base = followings.updatableAt
        FakeDateTimeProviderModule.instant = base
        FakeRemoteSourceModule.allFollowings =
            TwitchFollowings.createAtFetched(authUser.id, broadcaster(100), base.plusMillis(10))
        // exercise
        val actual = sut.flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(60) // PagingConfig.pageSize = 20, default initialLoadSize is 60 = (20 * 3)
            .allMatch { it.channel.iconUrl.isNotEmpty() }
    }

    @Test
    fun firstTimeToLoadSubscriptionPage_scrollToLastItem() = runTest {
        // setup
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
        var userDetails: List<TwitchUserDetail> = emptyList()
        internal var allFollowings: TwitchFollowings? = null

        @Singleton
        @Provides
        fun provide(): TwitchDataSource.Remote = object : TwitchDataSource.Remote {
            override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): List<TwitchUserDetail> {
                logD(tag = "FakeTwitchRemoteSource") { "findUsersById: $ids" }
                val table = userDetails.associateBy { it.id }
                return checkNotNull(ids).mapNotNull { table[it] }
            }

            override suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings {
                val f = checkNotNull(allFollowings)
                return if (f.followerId == userId) f else throw IllegalStateException("not found: $userId")
            }

            override suspend fun getAuthorizeUrl(state: String): String =
                throw NotImplementedError()

            override suspend fun fetchMe(): TwitchUserDetail = throw NotImplementedError()


            override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): TwitchStreams =
                throw NotImplementedError()

            override suspend fun fetchFollowedStreamSchedule(
                id: TwitchUser.Id,
                maxCount: Int,
            ): List<TwitchChannelSchedule> = throw NotImplementedError()

            override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
                throw NotImplementedError()

            override suspend fun fetchVideosByUserId(
                id: TwitchUser.Id,
                itemCount: Int,
            ): List<TwitchVideoDetail> = throw NotImplementedError()
        }
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
