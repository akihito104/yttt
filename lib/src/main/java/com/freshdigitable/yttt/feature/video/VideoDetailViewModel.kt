package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.LiveVideoSharedTransitionRoute
import com.freshdigitable.yttt.data.model.LiveVideoDetailAnnotated
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegate
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    findLiveVideoTable: IdBaseClassMap<FindLiveVideoDetailAnnotatedUseCase>,
    private val contextMenuDelegate: TimetableContextMenuDelegate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val videoId = LiveVideoSharedTransitionRoute.VideoDetail.getId(savedStateHandle)
    private val findLiveVideo = checkNotNull(findLiveVideoTable[videoId.type.java])
    fun fetchViewDetail(): LiveData<LiveVideoDetailAnnotated?> {
        return liveData(viewModelScope.coroutineContext) {
            val detail = findLiveVideo(videoId)
            if (detail == null) { // TODO: informing video is not found
                emit(null)
                return@liveData
            }
            emit(detail)
        }
    }

    val contextMenuItems = flowOf(videoId).map {
        contextMenuDelegate.setupMenu(it)
        contextMenuDelegate.findMenuItems(it)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun consumeMenuItem(item: TimetableMenuItem) {
        contextMenuDelegate.consumeMenuItem(item)
    }
}
