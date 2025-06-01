package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.asFreeChat
import com.freshdigitable.yttt.logE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun addVideoFromWorker(
        id: YouTubeVideo.Id,
        isFreeChat: Boolean = false,
    ): Result<List<YouTubeVideoExtended>> = repository.fetchVideoList(setOf(id))
        .map { v -> if (isFreeChat) v.map { it.asFreeChat() } else v }
        .onFailure { logE(throwable = it) { "addVideoFromWorker: $id" } }
        .onSuccess { repository.addVideo(it) }
}
