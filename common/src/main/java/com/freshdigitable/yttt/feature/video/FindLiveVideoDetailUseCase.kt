package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail

interface FindLiveVideoDetailUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): Result<LiveVideoDetail?>
}
