package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.di.LivePlatformKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

internal class YouTubeAnnotatableStringFactory : AnnotatableString.Factory {
    companion object {
        private const val PARENTHESIS = "()<>{}\\[\\]（）【】「」『』〖〗"
        private val YOUTUBE_URL_REGEX =
            Regex("""(?<!http(s)?://(www.)?)(www.)?youtube.com(/[^\s$PARENTHESIS]*)?""")
    }

    override fun urlAnnotation(annotatable: String): List<LinkAnnotationRange> {
        val url = super.urlAnnotation(annotatable)
        val youtubeUrl = YOUTUBE_URL_REGEX.findAll(annotatable)
            .filter { r -> url.all { !it.range.contains(r.range.first) } }
            .map {
                LinkAnnotationRange.Url(
                    range = it.range,
                    text = it.value,
                    url = "https://${it.value}",
                )
            }
        return (url + youtubeUrl).toList().sortedBy { it.range.first }
    }

    override fun accountUrlCandidate(account: String): List<String> = listOf(
        "https://youtube.com/$account",
        "https://twitter.com/${account.substring(1)}",
    )
}

@Module
@InstallIn(SingletonComponent::class)
interface YouTubeAnnotatableStringFactoryModule {
    companion object {
        @Provides
        @IntoMap
        @LivePlatformKey(YouTube::class)
        fun provideAnnotatableStringFactory(): AnnotatableString.Factory =
            YouTubeAnnotatableStringFactory()
    }
}
