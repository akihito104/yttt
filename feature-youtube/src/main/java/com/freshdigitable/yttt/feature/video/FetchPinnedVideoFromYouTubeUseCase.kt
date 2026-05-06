package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class FetchPinnedVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchPinnedVideoUseCase {
    override fun invoke(): Flow<List<YouTubeVideoExtended>> {
        return repository.fetchAllPinnedVideoList().map { p -> p.map { it.item } }
    }
}
