package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.model.AnnotatableString
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoDetailAnnotatedEntity
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveVideoDetail
import javax.inject.Inject

internal class FindLiveVideoFromYouTubeUseCase @Inject constructor(
    private val facade: YouTubeFacade,
) : FindLiveVideoUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideo? {
        check(id.type == YouTubeVideo.Id::class)
        val v = facade.fetchVideoList(setOf(id.mapTo())).firstOrNull()
        return v?.toLiveVideoDetail()
    }
}

internal class FindLiveVideoDetailAnnotatedFromYouTubeUseCase @Inject constructor(
    private val findLiveVideo: FindLiveVideoFromYouTubeUseCase,
) : FindLiveVideoDetailAnnotatedUseCase {
    override suspend fun invoke(id: LiveVideo.Id): LiveVideoDetailAnnotatedEntity? {
        val v = findLiveVideo(id) ?: return null
        check(v is LiveVideoDetail)
        return LiveVideoDetailAnnotatedEntity(
            detail = v,
            annotatableDescription = AnnotatableString.createForYouTube(v.description),
            annotatableTitle = AnnotatableString.createForYouTube(v.title),
        )
    }
}

internal fun AnnotatableString.Companion.createForYouTube(description: String): AnnotatableString =
    create(description) {
        listOf(
            "https://youtube.com/$it",
            "https://twitter.com/${it.substring(1)}",
        )
    }
