package com.freshdigitable.yttt.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.HorizontalPagerTabViewModel
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    private val useCases: ClassMap<LivePlatform, FetchSubscriptionListSourceUseCase>,
    platform: ClassMap<LivePlatform, LivePlatform>,
) : ViewModel(), HorizontalPagerTabViewModel<SubscriptionTabData> {
    private val sources = platform.map { (clz, p) ->
        p to checkNotNull(useCases[clz])()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }.toMap()
    override val initialTab: List<SubscriptionTabData> = platform.values
        .map { SubscriptionTabData(it, 0) }.sorted()
    override val tabData: StateFlow<List<SubscriptionTabData>> =
        combine(sources.map { (p, subs) ->
            subs.map { it.size }.distinctUntilChanged().map { SubscriptionTabData(p, it) }
        }) {
            it.toList().sorted()
        }.stateIn(viewModelScope, SharingStarted.Lazily, initialTab)

    fun items(tab: SubscriptionTabData): Flow<List<LiveSubscription>> =
        checkNotNull(sources[tab.platform])
}

@Immutable
class SubscriptionTabData(
    internal val platform: LivePlatform,
    private val count: Int,
) : TabData<SubscriptionTabData> {
    @Composable
    override fun title(): String = "${platform.name}($count)"

    override fun compareTo(other: SubscriptionTabData): Int =
        platform.name.compareTo(other.platform.name)
}
