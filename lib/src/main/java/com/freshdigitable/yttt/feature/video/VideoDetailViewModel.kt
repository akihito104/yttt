package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegate
import com.freshdigitable.yttt.feature.timetable.TimetableMenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val contextMenuDelegate: TimetableContextMenuDelegate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val videoId = savedStateHandle.toRoute<LiveVideo.Id>(navTypeMap)
    private val findLiveVideo = checkNotNull(findLiveVideoTable[videoId.type.java])
    private val annotatedString = checkNotNull(annotatedStringFactory[videoId.type.java])
    internal val detail: Flow<LiveVideoDetailItem?> = flowOf(videoId).map {
        val v = findLiveVideo(it) ?: return@map null
        LiveVideoDetailItem(
            video = v,
            annotatableDescription = annotatedString(v.description),
            annotatableTitle = annotatedString(v.title),
        )
    }

    val contextMenuItems = flowOf(videoId).map {
        contextMenuDelegate.setupMenu(it)
        contextMenuDelegate.findMenuItems(it)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun consumeMenuItem(item: TimetableMenuItem) {
        contextMenuDelegate.consumeMenuItem(item)
    }
}
