package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideo

interface FindLiveVideoUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo?
}
