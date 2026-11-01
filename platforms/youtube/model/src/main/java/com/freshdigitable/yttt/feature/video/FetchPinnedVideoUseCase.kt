package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import kotlinx.coroutines.flow.Flow

interface FetchPinnedVideoUseCase {
    operator fun invoke(): Flow<List<YouTubeVideoExtended>>
}
