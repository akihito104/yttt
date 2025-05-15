package com.freshdigitable.yttt.feature.channel

import androidx.compose.material3.SnackbarVisuals
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.MainNavRoute.ChannelDetail.toLiveChannelRoute
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
    private val _errorMessage = Channel<SnackbarVisuals>()
    val errorMessage: ReceiveChannel<SnackbarVisuals> get() = _errorMessage
    private val delegate = checkNotNull(delegateFactory[channelId.type.java])
        .create(channelId, viewModelScope, _errorMessage)

    override val tabs: List<ChannelDetailPageTab<*>> get() = delegate.tabs
    override val channelDetailBody: StateFlow<LiveChannelDetailBody?> = delegate.channelDetailBody
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    override val pagerContent: ChannelDetailDelegate.PagerContent
        get() = delegate.pagerContent

    override fun onCleared() {
        _errorMessage.close()
        viewModelScope.launch {
            delegate.clearForDetail()
        }
    }
}
