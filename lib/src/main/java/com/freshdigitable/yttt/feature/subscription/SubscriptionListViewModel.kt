package com.freshdigitable.yttt.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.create
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val pagerFactory: ClassMap<LivePlatform, PagerFactory<LiveSubscription>>,
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
