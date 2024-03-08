package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.model.toLiveVideoDetail
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionAccountAnnotation
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionHashTagAnnotation
import com.freshdigitable.yttt.feature.video.LiveVideoDetailAnnotated.Companion.descriptionUrlAnnotation
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
    override suspend fun invoke(id: LiveVideo.Id): LiveVideoDetailAnnotated? {
        val v = findLiveVideo(id) ?: return null
        check(v is LiveVideoDetail)
        val urlAnnotation = v.descriptionUrlAnnotation
        val hashtagAnnotation = v.descriptionHashTagAnnotation.filter { a ->
            urlAnnotation.map { it.range }.all { !it.contains(a.range.first) }
        }
        val accountAnnotation = v.descriptionAccountAnnotation {
            listOf(
                "https://youtube.com/$it",
                "https://twitter.com/${it.substring(1)}",
            )
        }.filter { a ->
            urlAnnotation.map { it.range }.all { !it.contains(a.range.first) }
        }
        val items = (urlAnnotation + hashtagAnnotation + accountAnnotation)
            .sortedBy { it.range.first }
        return LiveVideoDetailAnnotatedEntity(v, items)
    }
}
