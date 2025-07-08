package com.freshdigitable.yttt.feature.video

import android.net.Uri
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.Updatable.Companion.map
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.asFreeChat
import com.freshdigitable.yttt.logE
import javax.inject.Inject

class AddStreamUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) {
    suspend operator fun invoke(data: Input): Result<YouTubeVideoExtended> =
        repository.fetchVideoList(setOf(data.id))
            .onFailure { logE(throwable = it) { "addVideoFromWorker: $data" } }
            .map { v -> if (data.isFreeChat) v.map { u -> u.map { it.asFreeChat() } } else v }
            .onSuccess { repository.addVideo(it) }
            .map { it.first().item }

    class Input private constructor(
        val id: YouTubeVideo.Id,
        val isFreeChat: Boolean = false,
    ) {
        constructor(id: String, isFreeChat: Boolean = false) : this(YouTubeVideo.Id(id), isFreeChat)

        companion object {
            fun create(uri: Uri, isFreeChat: Boolean = false): Input? {
                if (uri.isYouTubeUri) {
                    return Input(uri.pathSegments[1], isFreeChat)
                }
                return null
            }

            private val Uri.isYouTubeUri: Boolean
                get() = scheme == "https" && host?.endsWith("youtube.com") == true
                    && pathSegments.firstOrNull() == "live"
        }
    }
}
