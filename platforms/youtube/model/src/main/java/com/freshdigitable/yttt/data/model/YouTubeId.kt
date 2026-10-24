package com.freshdigitable.yttt.data.model

interface YouTubeId : IdBase

object YouTube : LivePlatform {
    override val name: String = "YouTube"
    override val color: Long = BRAND_COLOR

    // https://brand.youtube/#color
    private const val BRAND_COLOR = 0xFFFF0033
}

inline fun <reified T : YouTubeId> IdBase.toYouTubeId(): T {
    (this as? LiveId)?.checkMappable<T>()
    return when (T::class) {
        YouTubeVideo.Id::class -> YouTubeVideo.Id(value) as T
        YouTubeChannel.Id::class -> YouTubeChannel.Id(value) as T
        else -> this.toLiveId()
    }
}
