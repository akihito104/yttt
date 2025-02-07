package com.freshdigitable.yttt.feature.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.MainNavRoute
import com.freshdigitable.yttt.data.model.AnnotatedLiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoThumbnail
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    delegateFactory: IdBaseClassMap<ChannelDetailDelegate.Factory>,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ChannelDetailDelegate {
    private val channelId = MainNavRoute.ChannelDetail.getChannelId(savedStateHandle)
    private val delegate = checkNotNull(delegateFactory[channelId.type.java]).create(channelId)

    override val tabs: List<ChannelPage> get() = delegate.tabs
    override val channelDetail: StateFlow<AnnotatedLiveChannelDetail?> = delegate.channelDetail
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    override val uploadedVideo: Flow<List<LiveVideoThumbnail>> get() = delegate.uploadedVideo
    override val channelSection: Flow<List<ChannelDetailChannelSection>> get() = delegate.channelSection
    override val activities: Flow<List<LiveVideo<*>>> get() = delegate.activities
}
