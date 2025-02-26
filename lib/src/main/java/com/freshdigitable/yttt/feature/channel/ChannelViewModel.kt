package com.freshdigitable.yttt.feature.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.MainNavRoute
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveChannelDetailBody
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ChannelDetailDelegate {
    val channelId = MainNavRoute.ChannelDetail.getChannelId(savedStateHandle)
    private val delegate = checkNotNull(delegateFactory[channelId.type.java]).create(channelId)

    override val tabs: List<ChannelDetailPageTab<*>> get() = delegate.tabs
    override val channelDetailBody: StateFlow<LiveChannelDetailBody?> = delegate.channelDetailBody
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    override val annotatedDetail: StateFlow<AnnotatableString> = delegate.annotatedDetail
        .mapNotNull { it }
        .stateIn(viewModelScope, SharingStarted.Lazily, AnnotatableString.empty())
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> get() = delegate.uploadedVideo

    @OptIn(FlowPreview::class)
    override val channelSection: Flow<List<ChannelDetailChannelSection>>
        get() = delegate.channelSection.debounce(timeoutMillis = 200)
    override val activities: Flow<List<LiveVideo<*>>> get() = delegate.activities

    override fun onCleared() {
        viewModelScope.launch {
            delegate.clearForDetail()
        }
    }
}
