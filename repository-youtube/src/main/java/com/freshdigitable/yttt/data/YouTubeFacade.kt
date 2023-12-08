package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeVideo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeFacade @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
        val videos = repository.fetchVideoList(ids)
        val unchecked = videos.filter { it.isFreeChat == null }
            .associateWith { isFreeChat(it) }
        val add = unchecked.entries.filter { (_, f) -> f }.map { it.key.id }
        val remove = unchecked.map { it.key.id } - add.toSet()
        if (add.isNotEmpty()) {
            repository.addFreeChatItems(add)
        }
        if (remove.isNotEmpty()) {
            repository.removeFreeChatItems(remove)
        }
        return repository.fetchVideoList(ids)
    }

    private fun isFreeChat(video: YouTubeVideo): Boolean {
        return regex.any { video.title.contains(it) }
    }

    suspend fun updateAsFreeChat() {
        val unchecked = repository.findAllUnfinishedVideos()
        val freeChat = unchecked.filter { it.isFreeChat == null }
            .filter { v -> regex.any { v.title.contains(it) } }
            .map { it.id }
        repository.addFreeChatItems(freeChat)
        val unfinished = unchecked.filter { it.isFreeChat == null }.map { it.id } - freeChat.toSet()
        repository.removeFreeChatItems(unfinished)
    }

    suspend fun addFreeChatFromWorker(id: YouTubeVideo.Id) {
        val v = repository.fetchVideoList(listOf(id))
        repository.addFreeChatItems(v.map { it.id })
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
