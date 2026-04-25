package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import javax.inject.Inject

internal class FetchPinnedVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FetchPinnedVideoUseCase {
    override suspend fun invoke(): Result<List<YouTubeVideoExtended>> {
        return repository.fetchAllPinnedVideoList().map { it.map { u -> u.item } }
    }
}
