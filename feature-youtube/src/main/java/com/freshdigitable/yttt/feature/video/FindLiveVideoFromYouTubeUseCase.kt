package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.create
import com.freshdigitable.yttt.logE
import javax.inject.Inject

internal class FindLiveVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FindLiveVideoUseCase {
    override suspend fun invoke(id: LiveVideo.Id): Result<LiveVideo<*>?> {
        check(id.type == YouTubeVideo.Id::class)
        return repository.fetchVideoList(setOf(id.mapTo()))
            .onFailure { logE(throwable = it) { "invoke: $id" } }
            .onSuccess { repository.addVideo(it) }
            .map { v -> v.firstOrNull()?.let { LiveVideo.create(it.item) } }
    }
}

internal class YouTubeAnnotatableStringFactory @Inject constructor() : AnnotatableStringFactory {
    override fun invoke(text: String): AnnotatableString = AnnotatableString.createForYouTube(text)
}

internal fun AnnotatableString.Companion.createForYouTube(description: String): AnnotatableString =
    create(description) {
        listOf(
            "https://youtube.com/$it",
            "https://twitter.com/${it.substring(1)}",
        )
    }
