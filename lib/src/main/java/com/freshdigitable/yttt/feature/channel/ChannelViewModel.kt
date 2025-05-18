package com.freshdigitable.yttt.feature.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.MainNavRoute.ChannelDetail.toLiveChannelRoute
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChannelViewModel.Factory::class)
class ChannelViewModel @AssistedInject constructor(
    delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
    savedStateHandle: SavedStateHandle,
    @Assisted private val sender: SnackbarMessageBus.Sender,
) : ViewModel(), ChannelDetailDelegate {
    @AssistedFactory
    interface Factory {
        fun create(messageSender: SnackbarMessageBus.Sender): ChannelViewModel
    }

    private val channelId = savedStateHandle.toLiveChannelRoute
    private val delegate = checkNotNull(delegateFactory[channelId.type.java])
        .create(channelId, viewModelScope, sender)

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
