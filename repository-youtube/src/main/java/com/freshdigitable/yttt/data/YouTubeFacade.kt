package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extendAsFreeChat
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideoExtended> {
        val map = repository.videos.value.associateBy { it.id }
        val cache = ids.associateWith { map[it] }
        val v = repository.fetchVideoList(ids.toSet())
        return v.map { it.extend(cache[it.id]) }
    }

    suspend fun addFreeChatFromWorker(id: YouTubeVideo.Id) {
        val v = repository.fetchVideoList(setOf(id))
        repository.addVideo(v.map { it.extendAsFreeChat() })
    }
}
