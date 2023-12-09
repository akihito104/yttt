package com.freshdigitable.yttt.feature.video

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.di.IdBaseClassMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val findLiveVideoTable: IdBaseClassMap<FindLiveVideoUseCase>,
) : ViewModel() {
    fun fetchViewDetail(id: LiveVideo.Id): LiveData<LiveVideo?> {
        val findLiveVideo = checkNotNull(findLiveVideoTable[id.type.java])
        return liveData(viewModelScope.coroutineContext) {
            val detail = findLiveVideo(id)
            if (detail == null) { // TODO: informing video is not found
                emit(null)
                return@liveData
            }
            emit(detail)
        }
    }
}
