package com.freshdigitable.yttt.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.testing.asSnapshot
import com.freshdigitable.yttt.data.FakeYouTubeClientImpl.Companion.subscriptionsRelevanceOrderedIds
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
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
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

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
        FakeDateTimeProviderModule.onTimeAdvanced = { client.current = it }
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
        client.channelDetails = emptyList()
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
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
        client.channelDetails = (0..<20).map { FakeYouTubeClient.channelDetail(it) }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        // exercise
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        assertThat(actual.map { it.id.value })
            .containsExactlyElementsIn(client.subscriptionsRelevanceOrderedIds.map { it.value })
            .inOrder()
        assertThat(repository.subscriptionsOrderedFetchedAt).isEqualTo(fetchedAt)
    }

    @Test
    fun loadFromPager_fetchAfterUpdatingTimelineTask_inRelevanceOrder() = testScope.runTest {
        // setup
        FakeAccountRepositoryModule.account = "account"
        client.channelDetails = (0..<20).map { FakeYouTubeClient.channelDetail(it) }
        FakeDateTimeProviderModule.instant = Instant.parse("2025-08-01T13:20:00Z")
        // exercise
        repository.fetchPagedSubscription(50, null, null).onSuccess {
            repository.addSubscriptionEtag(0, it.nextPageToken, it.eTag!!)
            repository.subscriptionsFetchedAt = it.cacheControl.fetchedAt!!
        }
        val fetchedAt = Instant.parse("2025-08-01T14:02:00Z")
        FakeDateTimeProviderModule.instant = fetchedAt
        val actual = pagerFactory.create(PagingConfig(20)).flow.asSnapshot()
        // verify
        assertThat(actual.map { it.id.value })
            .containsExactlyElementsIn(client.subscriptionsRelevanceOrderedIds.map { it.value })
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
    var current: Instant = Instant.EPOCH
    var channelDetails: List<YouTubeChannelDetail> = emptyList()
        set(value) {
            subscriptions = value.sortedBy { it.title }
                .map { subscription("s_${it.id.value}", it) }
                .toResponse(this)
            subscriptionsInRelevanceOrder = value.mapIndexed { i, c ->
                subscriptionRelevanceOrdered("s_${c.id.value}", c, i)
            }.toResponse(this)
            field = value
        }
    var subscriptions: Map<String?, () -> NetworkResponse<List<YouTubeSubscription>>> = emptyMap()
        private set
    var subscriptionsInRelevanceOrder: Map<String?, () -> NetworkResponse<List<YouTubeSubscriptionRelevanceOrdered>>> =
        emptyMap()
        private set

    override fun fetchSubscriptionRelevanceOrdered(
        pageSize: Int,
        offset: Int,
        token: String?,
    ): NetworkResponse<List<YouTubeSubscriptionRelevanceOrdered>> {
        logD { "fetchSubscriptionRelevanceOrdered: $pageSize,$offset,$token" }
        return if (subscriptionRelevanceOrdered != null) subscriptionRelevanceOrdered!!.invoke()
        else subscriptionsInRelevanceOrder[token]!!.invoke()
    }

    override fun fetchSubscription(
        pageSize: Int,
        token: String?,
        eTag: String?,
    ): NetworkResponse<List<YouTubeSubscription>> {
        logD { "fetchSubscription: $pageSize,$token,$eTag" }
        return if (subscription != null) subscription!!.invoke()
        else subscriptions[token]!!.invoke()
    }

    companion object {
        private val md = MessageDigest.getInstance("SHA-256")
        private fun <T : YouTubeSubscription> Collection<T>.toResponse(
            client: FakeYouTubeClientImpl,
        ): Map<String?, () -> NetworkResponse<List<T>>> = chunked(50).mapIndexed { i, c ->
            val key = if (i == 0) null else "token_$i"
            val nextToken = if (i == ceil(size / 50.0).toInt() - 1) null else "token_${i + 1}"
            val eTag = md.run {
                reset()
                digest(c.joinToString("") { it.id.value }.toByteArray()).toHexString()
            }
            val value = { NetworkResponse.create(c.toUpdatable(client.current), nextToken, eTag) }
            key to value
        }.toMap().ifEmpty {
            mapOf(null to { NetworkResponse.create(emptyList<T>().toUpdatable(client.current)) })
        }

        val FakeYouTubeClientImpl.subscriptionsRelevanceOrderedIds: List<YouTubeSubscription.Id>
            get() = subscriptionsInRelevanceOrder.values.map { it().item }.flatten().map { it.id }
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
