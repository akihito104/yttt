package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): List<YouTubeVideo> {
        val videos = repository.fetchVideoList(ids)
        val unchecked = videos.filter { !it.isArchived }
            .filter { it.isFreeChat == null }
        updateAsFreeChat(unchecked)
        val updated = repository.fetchVideoList(unchecked.map { it.id }.toSet())
        return videos.associateBy { it.id }.toMutableMap().apply {
            updated.forEach { this[it.id] = it }
        }.values.toList()
    }

    private fun isFreeChat(video: YouTubeVideo): Boolean {
        return regex.any { video.title.contains(it) }
    }

    suspend fun updateAsFreeChat(
        unchecked: Collection<YouTubeVideo> = repository.findAllUnfinishedVideos()
            .filter { it.isFreeChat == null },
    ) {
        val freeChat = unchecked.filter(::isFreeChat).map { it.id }.toSet()
        if (freeChat.isNotEmpty()) {
            repository.addFreeChatItems(freeChat)
        }
        val liveStream = unchecked.map { it.id }.toSet() - freeChat
        if (liveStream.isNotEmpty()) {
            repository.removeFreeChatItems(liveStream)
        }
    }

    suspend fun addFreeChatFromWorker(id: YouTubeVideo.Id) {
        val v = repository.fetchVideoList(setOf(id))
        repository.addFreeChatItems(v.map { it.id }.toSet())
    }

    companion object {
        private val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )
    }
}
