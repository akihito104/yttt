package com.freshdigitable.yttt.feature.video

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

    companion object {
        // https://stackoverflow.com/questions/36586166/android-patterns-web-url-broken
//        private val WEB_URL_REGEX = Patterns.WEB_URL.toRegex()
        private val WEB_URL_REGEX = Regex("""http(s)?://[-\w.]+(:(\d+))?(/[^\s)]*)?""")
        private val YOUTUBE_URL_REGEX = Regex("""(?<!http(s)?://)(www.)?youtube.com(/[^\s)]*)?""")
        val LiveVideoDetail.descriptionUrlAnnotation: List<LinkAnnotationRange>
            get() = (WEB_URL_REGEX.findAll(description).map {
                LinkAnnotationRange(
                    range = it.range,
                    url = it.value,
                )
            } + YOUTUBE_URL_REGEX.findAll(description).map {
                LinkAnnotationRange(
                    range = it.range,
                    url = "https://${it.value}",
                    text = it.value,
                )
            }).toList().sortedBy { it.range.first }
    }
}

data class LiveVideoDetailAnnotatedEntity(
    private val detail: LiveVideoDetail,
    override val descriptionAnnotationRangeItems: List<LinkAnnotationRange>,
) : LiveVideoDetailAnnotated, LiveVideoDetail by detail

data class LinkAnnotationRange(
    val range: IntRange,
    val url: String,
    val text: String = url,
    val tag: String = "URL",
)
