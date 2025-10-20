package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.LiveVideoSharedTransitionRoute.VideoDetail.toLiveVideoRoute
import com.freshdigitable.yttt.compose.SnackbarMessage
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.compose.TopAppBarMenuItem
import com.freshdigitable.yttt.compose.onFailureWithSnackbarMessage
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuSelector
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = VideoDetailViewModel.Factory::class)
class VideoDetailViewModel @AssistedInject constructor(
    findLiveVideoTable: IdBaseClassMap<FindLiveVideoUseCase>,
    annotatedStringFactory: IdBaseClassMap<AnnotatableStringFactory>,
    menuSelectorMap: IdBaseClassMap<TimetableContextMenuSelector>,
    savedStateHandle: SavedStateHandle,
    @Assisted sender: SnackbarMessageBus.Sender,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(sender: SnackbarMessageBus.Sender): VideoDetailViewModel
    }

    private val videoId = savedStateHandle.toLiveVideoRoute
    private val findLiveVideo = checkNotNull(findLiveVideoTable[videoId.type.java])
    private val annotatedString = checkNotNull(annotatedStringFactory[videoId.type.java])
    internal val detail: Flow<LiveVideoDetailItem?> = flowOf(videoId).map { id ->
        findLiveVideo(id)
            .onFailureWithSnackbarMessage(sender)
            .onSuccess { if (it == null) sender.send(SnackbarMessage("Video not found")) }
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

    private val menuSelector = menuSelectorMap.getValue(videoId.type.java)
    val contextMenuItems: Flow<List<TopAppBarMenuItem>> = flowOf(videoId).filterNotNull().map { id ->
        val items = menuSelector.setupMenuItems(id)
        items.menuItems.map {
            TopAppBarMenuItem.inOthers(it.text) {
                items.consumeMenuItem(it)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
