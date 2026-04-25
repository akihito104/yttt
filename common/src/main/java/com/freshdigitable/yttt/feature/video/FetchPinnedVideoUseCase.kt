package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.YouTubeVideoExtended

interface FetchPinnedVideoUseCase {
    suspend operator fun invoke(): Result<List<YouTubeVideoExtended>>
}
