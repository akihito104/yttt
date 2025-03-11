package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.TopAppBarMenuItem
import com.freshdigitable.yttt.compose.navigation.NavTypedComposable.Companion.toLiveVideoRoute
import com.freshdigitable.yttt.di.IdBaseClassMap
import com.freshdigitable.yttt.feature.timetable.TimetableContextMenuDelegate
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
    private val videoId = savedStateHandle.toLiveVideoRoute
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
}
