package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.compose.LiveVideoSharedTransitionRoute
import com.freshdigitable.yttt.data.model.LiveVideoDetailAnnotated
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    findLiveVideoTable: IdBaseClassMap<FindLiveVideoDetailAnnotatedUseCase>,
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
}
