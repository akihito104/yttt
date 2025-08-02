package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionRelevanceOrdered
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.di.YouTubeAccountDataSourceModule
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.MediatorResultSubject.Companion.assertThat
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.toUpdatableFromRemote
import com.google.common.truth.Truth.assertThat
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoMap
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
    private val client = FakeYouTubeClientImpl()

    @Before
    fun setup() {
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T14:00:00Z")
        FakeYouTubeClientModule.client = client
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        FakeDateTimeProviderModule.clear()
    }

    @Test
    fun initialize_needsRefreshWhenUpdatable() = testScope.runTest {
        // setup
        repository.subscriptionsOrderedFetchedAt = Instant.parse("2025-08-01T12:00:00Z")
        // exercise
        val actual = remoteMediator.initialize()
        // verify
        assertThat(actual).isEqualTo(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
    }

    @Test
    fun initialize_skipUntilMaxAge() = testScope.runTest {
        // setup
        repository.subscriptionsOrderedFetchedAt = Instant.parse("2025-08-01T12:00:01Z")
        // exercise
        val actual = remoteMediator.initialize()
        // verify
        assertThat(actual).isEqualTo(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH)
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
        assertThat(actual).isSuccess(endOfPaginationReached = true)
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
        assertThat(actual).isSuccess(endOfPaginationReached = true)
    }

    @Test
    fun load_hasAccount_noItemsLoadedAndReturnsSuccess() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        client.subscriptionRelevanceOrdered = {
            NetworkResponse.create(
                emptyList<YouTubeSubscriptionRelevanceOrdered>().toUpdatableFromRemote(fetchedAt)
            )
        }
        // exercise
        val actual = remoteMediator.load(
            LoadType.REFRESH,
            PagingState(emptyList(), 0, PagingConfig(20), 0),
        )
        // verify
        assertThat(actual).isSuccess(endOfPaginationReached = true)
        assertThat(repository.subscriptionsOrderedFetchedAt).isEqualTo(fetchedAt)
    }

    @Test
    fun loadFromPager() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        val subs = (0..<20).map {
            val channel = FakeYouTubeClient.channelDetail(it)
            subscriptionRelevanceOrdered("s_${channel.id.value}", channel, it)
        }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        client.subscriptionRelevanceOrdered = {
            NetworkResponse.create(subs.toUpdatableFromRemote(fetchedAt))
        }
        // exercise
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(20)
        assertThat(actual.map { it.id.value }).containsExactlyElementsIn(subs.map { it.id.value })
            .inOrder()
        assertThat(repository.subscriptionsOrderedFetchedAt).isEqualTo(fetchedAt)
    }

    @Test
    fun loadFromPager_fetchAfterUpdatingTimelineTask_inRelevanceOrder() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        val channels = (0..<20).map { FakeYouTubeClient.channelDetail(it) }
        val subs = channels.mapIndexed { i, c ->
            subscriptionRelevanceOrdered("s_${c.id.value}", c, i)
        }
        client.subscription = {
            NetworkResponse.create(
                channels.sortedBy { it.title }
                    .map { c -> FakeYouTubeClient.subscription("s_${c.id.value}", c) }
                    .toUpdatableFromRemote(Instant.parse("2025-08-01T13:20:00Z")),
                null, "eTag0",
            )
        }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        client.subscriptionRelevanceOrdered = {
            NetworkResponse.create(subs.toUpdatableFromRemote(fetchedAt))
        }
        // exercise
        repository.fetchPagedSubscription(50, null, null).onSuccess {
            repository.addSubscriptionEtag(0, it.nextPageToken, it.eTag!!)
            repository.subscriptionsFetchedAt = it.cacheControl.fetchedAt!!
        }
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        assertThat(actual).hasSize(20)
        assertThat(actual.map { it.id.value }).containsExactlyElementsIn(subs.map { it.id.value })
            .inOrder()
        assertThat(repository.subscriptionsOrderedFetchedAt).isEqualTo(fetchedAt)
    }
}

fun subscriptionRelevanceOrdered(
    id: String,
    channel: YouTubeChannel,
    order: Int,
): YouTubeSubscriptionRelevanceOrdered = object : YouTubeSubscriptionRelevanceOrdered,
    YouTubeSubscription by FakeYouTubeClient.subscription(id, channel) {
    override val order: Int get() = order
}

internal class FakeYouTubeClientImpl(
    var subscriptionRelevanceOrdered: (() -> NetworkResponse<List<YouTubeSubscriptionRelevanceOrdered>>)? = null,
    var subscription: (() -> NetworkResponse<List<YouTubeSubscription>>)? = null,
) : FakeYouTubeClient() {
    override fun fetchSubscriptionRelevanceOrdered(
        pageSize: Int,
        offset: Int,
        token: String?,
    ): NetworkResponse<List<YouTubeSubscriptionRelevanceOrdered>> {
        logD { "fetchSubscriptionRelevanceOrdered: $pageSize,$offset,$token" }
        return subscriptionRelevanceOrdered!!.invoke()
    }

    override fun fetchSubscription(
        pageSize: Int,
        token: String?,
        eTag: String?
    ): NetworkResponse<List<YouTubeSubscription>> {
        logD { "fetchSubscription: $pageSize,$token,$eTag" }
        return subscription!!.invoke()
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
