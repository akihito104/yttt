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
            .associateWith { isFreeChat(it) }
        val add = unchecked.entries.filter { (_, f) -> f }.map { it.key.id }.toSet()
        val remove = unchecked.map { it.key.id }.toSet() - add
        if (add.isNotEmpty()) {
            repository.addFreeChatItems(add)
        }
        if (remove.isNotEmpty()) {
            repository.removeFreeChatItems(remove)
        }
        val updated = repository.fetchVideoList(add + remove)
        return videos.associateBy { it.id }.toMutableMap().apply {
            updated.forEach { this[it.id] = it }
        }.values.toList()
    }

    private fun isFreeChat(video: YouTubeVideo): Boolean {
        return regex.any { video.title.contains(it) }
    }

    suspend fun updateAsFreeChat() {
        val unchecked = repository.findAllUnfinishedVideos()
        val freeChat = unchecked.filter { it.isFreeChat == null }
            .filter { v -> regex.any { v.title.contains(it) } }
            .map { it.id }.toSet()
        repository.addFreeChatItems(freeChat)
        val unfinished =
            unchecked.filter { it.isFreeChat == null }.map { it.id }.toSet() - freeChat.toSet()
        repository.removeFreeChatItems(unfinished)
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
