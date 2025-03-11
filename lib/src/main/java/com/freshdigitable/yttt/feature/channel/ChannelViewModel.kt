package com.freshdigitable.yttt.feature.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.navigation.NavTypedComposable.Companion.toLiveChannelRoute
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ChannelDetailDelegate {
    private val channelId = savedStateHandle.toLiveChannelRoute
    private val delegate = checkNotNull(delegateFactory[channelId.type.java])
        .create(channelId, viewModelScope)

    override val tabs: List<ChannelDetailPageTab<*>> get() = delegate.tabs
    override val channelDetailBody: StateFlow<LiveChannelDetailBody?> = delegate.channelDetailBody
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    override val pagerContent: ChannelDetailDelegate.PagerContent
        get() = delegate.pagerContent

    override fun onCleared() {
        viewModelScope.launch {
            delegate.clearForDetail()
        }
    }
}
