package com.freshdigitable.yttt.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.PagingSourceFunction
import com.freshdigitable.yttt.data.source.RemoteMediatorFactory
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val pagerFactory: SubscriptionPagerFactory,
    private val accountRepository: ClassMap<LivePlatform, AccountRepository>,
    platformMap: ClassMap<LivePlatform, LivePlatform>,
) : ViewModel() {
    private val platform: List<LivePlatform> = platformMap
        .map { it.value to checkNotNull(accountRepository[it.key]).hasAccount() }
        .filter { it.second }.map { it.first }
        .sortedBy { it.name }
    val tabCount: Int = platform.size
    val pagingData: List<Flow<PagingData<LiveSubscription>>>

    init {
        val p = platformMap.map { (_, p) ->
            p to pagerFactory.create(p, pagingConfig).flow.cachedIn(viewModelScope)
        }.toMap()
        pagingData = platform.map { checkNotNull(p[it]) }
    }

    fun tabText(index: Int, count: Int): String =
        SubscriptionTabData.titleText(platform[index], count)

    companion object {
        private val pagingConfig = PagingConfig(pageSize = 10)
    }
}

@Immutable
class SubscriptionTabData(
    internal val platform: LivePlatform,
    private val count: Int,
) : TabData<SubscriptionTabData> {
    @Composable
    override fun title(): String = titleText(platform, count)

    override fun compareTo(other: SubscriptionTabData): Int =
        platform.name.compareTo(other.platform.name)

    companion object {
        fun titleText(platform: LivePlatform, count: Int): String = "${platform.name}($count)"
    }
}

@Singleton
class SubscriptionPagerFactory @Inject constructor(
    pagingSourceFunctions: ClassMap<LivePlatform, PagingSourceFunction<LiveSubscription>>,
    remoteMediatorFactories: ClassMap<LivePlatform, RemoteMediatorFactory<LiveSubscription>>,
    platformMap: ClassMap<LivePlatform, LivePlatform>,
) {
    @OptIn(ExperimentalPagingApi::class)
    private val pagerFactory = platformMap.map { (clz, p) ->
        val pagingSourceFunction = checkNotNull(pagingSourceFunctions[clz])
        val remoteMediatorFactory = checkNotNull(remoteMediatorFactories[clz])
        p to object : PagerFactory<Unit, LiveSubscription> {
            override fun create(
                query: Unit,
                config: PagingConfig,
            ): Pager<Int, LiveSubscription> = Pager(
                config = config,
                remoteMediator = remoteMediatorFactory.create(),
                pagingSourceFactory = { pagingSourceFunction.create() },
            )
        }
    }.toMap()

    fun create(
        platform: LivePlatform,
        config: PagingConfig,
    ): Pager<Int, LiveSubscription> = checkNotNull(pagerFactory[platform]).create(Unit, config)
}
