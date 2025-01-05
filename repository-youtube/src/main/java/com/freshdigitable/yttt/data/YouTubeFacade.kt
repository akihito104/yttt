package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extendAsFreeChat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun addFreeChatFromWorker(id: YouTubeVideo.Id) {
        val v = repository.fetchVideoList(setOf(id))
        val extended = v.map { it.extendAsFreeChat() }
        repository.addVideo(extended)
    }
}
