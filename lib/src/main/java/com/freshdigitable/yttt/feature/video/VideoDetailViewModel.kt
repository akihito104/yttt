package com.freshdigitable.yttt.feature.video

import androidx.compose.material3.SnackbarVisuals
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.LiveVideoSharedTransitionRoute.VideoDetail.toLiveVideoRoute
import com.freshdigitable.yttt.compose.SnackbarMessage
import com.freshdigitable.yttt.compose.TopAppBarMenuItem
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    findLiveVideoTable: IdBaseClassMap<FindLiveVideoUseCase>,
    annotatedStringFactory: IdBaseClassMap<AnnotatableStringFactory>,
    contextMenuDelegateFactory: TimetableContextMenuDelegate.Factory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val videoId = savedStateHandle.toLiveVideoRoute
    private val findLiveVideo = checkNotNull(findLiveVideoTable[videoId.type.java])
    private val annotatedString = checkNotNull(annotatedStringFactory[videoId.type.java])
    private val _errorMessageChannel = Channel<SnackbarVisuals>()
    val errorMessage: ReceiveChannel<SnackbarVisuals> = _errorMessageChannel
    private val contextMenuDelegate = contextMenuDelegateFactory.create(_errorMessageChannel)
    internal val detail: Flow<LiveVideoDetailItem?> = flowOf(videoId).map { id ->
        findLiveVideo(id)
            .onFailure { _errorMessageChannel.send(SnackbarMessage.fromThrowable(it)) }
            .onSuccess { if (it == null) _errorMessageChannel.send(SnackbarMessage("Video not found")) }
            .map { v ->
                v?.let {
                    LiveVideoDetailItem(
                        video = it,
                        annotatableDescription = annotatedString(it.description),
                        annotatableTitle = annotatedString(it.title),
                    )
                }
            }.getOrNull()
    }

    val contextMenuItems: Flow<List<TopAppBarMenuItem>> =
        flowOf(videoId).map { id ->
            contextMenuDelegate.setupMenu(id)
            contextMenuDelegate.findMenuItems(id)
        }.map { items ->
            items.map {
                TopAppBarMenuItem.inOthers(it.text) {
                    contextMenuDelegate.consumeMenuItem(it)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    override fun onCleared() {
        super.onCleared()
        _errorMessageChannel.close()
    }
}
