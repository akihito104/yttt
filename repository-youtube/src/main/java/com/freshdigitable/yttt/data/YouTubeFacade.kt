package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.asFreeChat
import com.freshdigitable.yttt.logE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun addFreeChatFromWorker(id: YouTubeVideo.Id) {
        val videoRes = repository.fetchVideoList(setOf(id)).map { v -> v.map { it.asFreeChat() } }
            .onFailure { logE(throwable = it) { "addFreeChatFromWorker: $id" } }
        val extended = videoRes.getOrNull() ?: return
        repository.addVideo(extended)
    }
}
