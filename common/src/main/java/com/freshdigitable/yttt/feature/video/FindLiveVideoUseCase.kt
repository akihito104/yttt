package com.freshdigitable.yttt.feature.video

import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import java.util.regex.Pattern

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
        private val YOUTUBE_URL_REGEX =
            Regex("""(?<!http(s)?://(www.)?)(www.)?youtube.com(/[^\s)]*)?""")
        val LiveVideoDetail.descriptionUrlAnnotation: List<LinkAnnotationRange>
            get() = (WEB_URL_REGEX.findAll(description).map {
                LinkAnnotationRange.url(range = it.range, text = it.value)
            } + YOUTUBE_URL_REGEX.findAll(description).map {
                LinkAnnotationRange.url(
                    range = it.range,
                    text = it.value,
                    url = "https://${it.value}",
                )
            }).toList().sortedBy { it.range.first }
        private val REGEX_HASHTAG =
        // Pattern.UNICODE_CHARACTER_CLASS is not supported
//            Pattern.compile("""([#|＃])(\w)+[^\s()]*""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
            Pattern.compile("""([#|＃])[^\s　()<>{}\[\]（）【】「」『』#$'",.;:|\\]+""").toRegex()
        val LiveVideoDetail.descriptionHashTagAnnotation: List<LinkAnnotationRange>
            get() = REGEX_HASHTAG.findAll(description).map {
                LinkAnnotationRange.hashtag(range = it.range, text = it.value)
            }.toList()
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
    val tag: String,
) {
    companion object {
        fun url(range: IntRange, text: String, url: String = text): LinkAnnotationRange =
            LinkAnnotationRange(range = range, url = url, text = text, tag = "URL")

        fun hashtag(range: IntRange, text: String): LinkAnnotationRange =
            LinkAnnotationRange(
                range = range,
                text = text,
                url = "https://twitter.com/search?q=%23${text.drop(1)}",
                tag = "hashtag",
            )

        fun LinkAnnotationRange.ellipsizeTextAt(length: Int): String {
            if (tag != "URL") return text
            if (text.length <= length) return text
            return "${text.substring(0 until length)}..."
        }
    }
}
