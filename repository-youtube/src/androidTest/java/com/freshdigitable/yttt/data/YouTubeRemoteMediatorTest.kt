package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery.Order
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.SubscriptionItemJson
import com.freshdigitable.yttt.test.SubscriptionItemJson.Companion.eTag
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.responseJson
import com.freshdigitable.yttt.test.shouldBeSuccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoMap
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalPagingApi::class)
@HiltAndroidTest
class YouTubeRemoteMediatorTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    internal lateinit var remoteMediator: YouTubeRemoteMediator

    @Inject
    internal lateinit var repository: YouTubeRepository

    @Inject
    internal lateinit var pagerFactory: YouTubeSubscriptionPagerFactory

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule()

    @get:Rule(order = 2)
    val server = MockServerRule()

    @Before
    fun setup() {
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T14:00:00Z")
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        FakeDateTimeProviderModule.clear()
    }

    @Test
    fun initialize_needsRefreshWhenUpdatable() = testScope.runTest {
        // setup
        repository.subscriptionsRelevanceOrderedFetchedAt = Instant.parse("2025-08-01T12:00:00Z")
        // exercise
        val actual = remoteMediator.initialize()
        // verify
        actual shouldBe RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    @Test
    fun initialize_skipUntilMaxAge() = testScope.runTest {
        // setup
        repository.subscriptionsRelevanceOrderedFetchedAt = Instant.parse("2025-08-01T12:00:01Z")
        // exercise
        val actual = remoteMediator.initialize()
        // verify
        actual shouldBe RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH
    }

    @Test
    fun load_hasNoAccount_noItemsLoadedAndReturnsSuccess() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = null
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), 0, PagingConfig(20), 0),
        )
        // verify
        actual.shouldBeSuccess(endOfPaginationReached = true)
    }

    @Test
    fun load_hasAccount_noOpToAppendLoadTypeAndReturnsSuccess() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        // exercise
        val actual = remoteMediator.load(
            LoadType.APPEND,
            PagingState(emptyList(), 0, PagingConfig(20), 0),
        )
        // verify
        actual.shouldBeSuccess(endOfPaginationReached = true)
    }

    @Test
    fun load_hasAccount_noItemsLoadedAndReturnsSuccess() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        server.setClient(
            subscription = { _, o ->
                check(o == Order.RELEVANCE)
                emptyList<SubscriptionItemJson>().responseJson(pageToken = null)
            },
        )
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), 0, PagingConfig(20), 0),
        )
        // verify
        actual.shouldBeSuccess(endOfPaginationReached = true)
        repository.subscriptionsRelevanceOrderedFetchedAt shouldBe fetchedAt
    }

    @Test
    fun loadFromPager() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        val subs = (0..<20).map { "channel_$it" }.map { SubscriptionItemJson("s_$it", it, "title_") }
        server.setClient(
            subscription = { _, o ->
                check(o == Order.RELEVANCE)
                subs.responseJson(pageToken = null, eTag = subs.eTag())
            },
        )
        // exercise
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        actual.map { it.id.value }.shouldContainInOrder(subs.map { it.id })
        repository.subscriptionsRelevanceOrderedFetchedAt shouldBe fetchedAt
    }

    @Test
    fun loadFromPager_fetchAfterUpdatingTimetableTask_inRelevanceOrder() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T13:20:00Z")
        val subs = (0..<20).map { "channel_$it" }.map { SubscriptionItemJson("s_$it", it, "title_") }
        server.setClient(
            subscription = { _, o ->
                check(o == Order.RELEVANCE)
                subs.responseJson(pageToken = null, eTag = subs.eTag())
            },
        )
        // exercise
        repository.fetchPagedSubscription(
            object : YouTubeSubscriptionQuery {
                override val offset: Int get() = 0
                override val nextPageToken: String? get() = null
                override val eTag: String? get() = null
                override val order: Order get() = Order.ALPHABETICAL
            },
        ).onSuccess {
            repository.syncSubscriptionList(it.item.map { i -> i.id }.toSet(), emptyList())
            repository.subscriptionsFetchedAt = it.cacheControl.fetchedAt!!
        }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        actual.map { it.id.value }.shouldContainInOrder(subs.map { it.id })
        repository.subscriptionsRelevanceOrderedFetchedAt shouldBe fetchedAt
    }
}

@Module
@TestInstallIn(
    replaces = [YouTubeAccountDataSourceModule::class],
    components = [SingletonComponent::class],
)
interface FakeAccountRepositoryModule {
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
