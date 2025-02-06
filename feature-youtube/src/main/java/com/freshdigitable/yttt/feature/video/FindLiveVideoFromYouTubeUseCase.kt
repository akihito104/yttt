package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoForDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.feature.create
import javax.inject.Inject

internal class FindLiveVideoFromYouTubeUseCase @Inject constructor(
    private val repository: YouTubeRepository,
) : FindLiveVideoUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideo? {
        check(id.type == YouTubeVideo.Id::class)
        val v = repository.fetchVideoList(setOf(id.mapTo())).firstOrNull() ?: return null
        return LiveVideo.create(v)
    }
}

internal class FindLiveVideoDetailAnnotatedFromYouTubeUseCase @Inject constructor(
    private val findLiveVideo: FindLiveVideoFromYouTubeUseCase,
) : FindLiveVideoDetailAnnotatedUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideoForDetail? {
        val v = findLiveVideo(id) ?: return null
        return YouTubeLiveVideoForDetail(v)
    }
}

internal data class YouTubeLiveVideoForDetail(
    override val video: LiveVideo,
) : LiveVideoForDetail {
    override val annotatableDescription: AnnotatableString
        get() = AnnotatableString.createForYouTube(video.description)
    override val annotatableTitle: AnnotatableString
        get() = AnnotatableString.createForYouTube(video.title)
}

internal fun AnnotatableString.Companion.createForYouTube(description: String): AnnotatableString =
    create(description) {
        listOf(
            "https://youtube.com/$it",
            "https://twitter.com/${it.substring(1)}",
        )
    }
