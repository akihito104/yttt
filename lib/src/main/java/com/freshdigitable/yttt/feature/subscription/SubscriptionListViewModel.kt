package com.freshdigitable.yttt.feature.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TabData
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.di.ClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {
    private val sortedPlatform = platform.values.sortedBy { it.name }
    private val table = platform.entries.associate { (k, v) -> v to requireNotNull(useCases[k]) }
    val sources = table.entries.associate { (p, uc) ->
        p to uc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
    private val initialTab: List<TabData> = sortedPlatform.map { SubscriptionTabData(it, 0) }
    val tabData: StateFlow<List<TabData>> = combine(sources.entries.map { (p, f) ->
        f.map { it.size }.distinctUntilChanged().map { SubscriptionTabData(p, it) }
    }) {
        it.toList().sorted()
    }.stateIn(viewModelScope, SharingStarted.Lazily, initialTab)
}

@Immutable
internal class SubscriptionTabData(
    internal val platform: LivePlatform,
    private val count: Int
) : TabData {
    @Composable
    override fun title(): String = "${platform.name}($count)"

    override fun compareTo(other: TabData): Int {
        val o = other as? SubscriptionTabData ?: return -1
        return platform.name.compareTo(o.platform.name)
    }
}
