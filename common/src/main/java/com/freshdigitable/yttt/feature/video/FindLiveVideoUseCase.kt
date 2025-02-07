package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoForDetail

interface FindLiveVideoUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo<*>?
}

interface FindLiveVideoDetailAnnotatedUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideoForDetail?
}
