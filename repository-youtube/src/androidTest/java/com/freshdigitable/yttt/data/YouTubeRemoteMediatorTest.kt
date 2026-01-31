package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.FakeYouTubeClientImpl.Companion.subscriptionsRelevanceOrderedIds
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.SubscriptionItemJson
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.YouTubeResponseJson
import com.freshdigitable.yttt.test.shouldBeSuccess
import com.freshdigitable.yttt.test.subscriptionJson
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
    private val client = FakeYouTubeClientImpl()

    @Before
    fun setup() {
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T14:00:00Z")
        FakeDateTimeProviderModule.onTimeAdvanced = { client.current = it }
        server.setClient(client)
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
        client.channelDetails = emptyList()
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
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
        client.channelDetails = (0..<20).map { FakeYouTubeClient.channelDetail(it) }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        // exercise
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        actual.map { it.id.value }
            .shouldContainInOrder(client.subscriptionsRelevanceOrderedIds)
        repository.subscriptionsRelevanceOrderedFetchedAt shouldBe fetchedAt
    }

    @Test
    fun loadFromPager_fetchAfterUpdatingTimetableTask_inRelevanceOrder() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        client.channelDetails = (0..<20).map { FakeYouTubeClient.channelDetail(it) }
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T13:20:00Z")
        // exercise
        repository.fetchPagedSubscription(
            object : YouTubeSubscriptionQuery {
                override val offset: Int get() = 0
                override val nextPageToken: String? get() = null
                override val eTag: String? get() = null
                override val order: YouTubeSubscriptionQuery.Order get() = YouTubeSubscriptionQuery.Order.ALPHABETICAL
            },
        ).onSuccess {
            repository.syncSubscriptionList(it.item.map { i -> i.id }.toSet(), emptyList())
            repository.subscriptionsFetchedAt = it.cacheControl.fetchedAt!!
        }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        actual.map { it.id.value }
            .shouldContainInOrder(client.subscriptionsRelevanceOrderedIds)
        repository.subscriptionsRelevanceOrderedFetchedAt shouldBe fetchedAt
    }
}

internal class FakeYouTubeClientImpl : FakeYouTubeClient {
    var current: Instant = Instant.EPOCH
    var channelDetails: List<YouTubeChannelDetail> = emptyList()
        set(value) {
            subscriptions = value.sortedBy { it.title }.toResponse()
            subscriptionsInRelevanceOrder = value.toResponse()
            field = value
        }
    var subscriptions: List<Pair<String?, () -> List<SubscriptionItemJson>>> = emptyList()
        private set
    var subscriptionsInRelevanceOrder: List<Pair<String?, () -> List<SubscriptionItemJson>>> = emptyList()
        private set

    override fun fetchSubscription(nextPageToken: String?, order: String): YouTubeResponseJson {
        logD { "fetchSubscription: $nextPageToken, $order" }
        val sub = when (YouTubeSubscriptionQuery.Order.valueOf(order.uppercase())) {
            YouTubeSubscriptionQuery.Order.ALPHABETICAL -> subscriptions
            YouTubeSubscriptionQuery.Order.RELEVANCE -> subscriptionsInRelevanceOrder
        }
        val index = sub.indexOfFirst { it.first == nextPageToken }
        val pageToken = sub.getOrNull(index + 1)?.first
        return subscriptionJson(pageToken = pageToken, items = sub[index].second())
    }

    companion object {
        private fun Collection<YouTubeChannelDetail>.toResponse(): List<Pair<String?, () -> List<SubscriptionItemJson>>> =
            chunked(50).mapIndexed { i, c ->
                val key = if (i == 0) null else "token_$i"
                key to { c.map { SubscriptionItemJson("s_${it.id.value}", it.id.value, it.title) } }
            }.ifEmpty {
                listOf(null to { emptyList() })
            }

        val FakeYouTubeClientImpl.subscriptionsRelevanceOrderedIds: List<String>
            get() = subscriptionsInRelevanceOrder.map { it.second() }.flatten().map { it.id }
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
