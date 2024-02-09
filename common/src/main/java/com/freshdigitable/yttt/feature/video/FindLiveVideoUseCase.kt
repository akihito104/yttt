package com.freshdigitable.yttt.feature.video

import android.util.Patterns
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail

interface FindLiveVideoUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideo?
}

interface FindLiveVideoDetailAnnotatedUseCase {
    suspend operator fun invoke(id: LiveVideo.Id): LiveVideoDetailAnnotated?
}

interface LiveVideoDetailAnnotated : LiveVideoDetail {
    val descriptionAnnotationRangeItems: List<LinkAnnotationRange>
        get() {
            val urlRanges = WEB_URL_REGEX.findAll(description).map {
                LinkAnnotationRange(
                    range = it.range,
                    url = it.value,
                )
            }.toList()
            return urlRanges
        }

    companion object {
        private val WEB_URL_REGEX = Patterns.WEB_URL.toRegex()
    }
}

data class LinkAnnotationRange(
    val range: IntRange,
    val url: String,
    val text: String = url,
    val tag: String = "URL",
)
