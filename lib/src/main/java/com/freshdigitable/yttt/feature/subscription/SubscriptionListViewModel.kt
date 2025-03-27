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
import com.freshdigitable.yttt.di.LivePlatformMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    accountRepository: LivePlatformMap<AccountRepository>,
    pagerFactory: LivePlatformMap<PagerFactory<LiveSubscription>>,
) : ViewModel() {
    private val platform: List<LivePlatform> = accountRepository.filter { it.value.hasAccount() }
        .map { it.key }
        .sortedBy { it.name }
    val tabCount: Int = platform.size
    val pagingData: List<Flow<PagingData<LiveSubscription>>> = platform.map {
        checkNotNull(pagerFactory[it]).create(pagingConfig).flow.cachedIn(viewModelScope)
    }

    fun tabText(index: Int, count: Int): String =
        SubscriptionTabData.titleText(platform[index], count)

    companion object {
        private val pagingConfig = PagingConfig(pageSize = 10)
    }
}

@Immutable
internal class SubscriptionTabData(
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
