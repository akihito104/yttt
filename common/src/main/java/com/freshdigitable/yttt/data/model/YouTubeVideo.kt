package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isFreeChatTitle
import java.math.BigInteger
import java.time.Instant

interface YouTubeVideo {
    val id: Id
    val title: String
    val channel: YouTubeChannel
    val thumbnailUrl: String
    val scheduledStartDateTime: Instant?
    val scheduledEndDateTime: Instant?
    val actualStartDateTime: Instant?
    val actualEndDateTime: Instant?
    val description: String
    val viewerCount: BigInteger?
    val liveBroadcastContent: BroadcastType?
    fun needsUpdate(current: Instant): Boolean

    fun isLiveStream(): Boolean = isNowOnAir() || isUpcoming()
    fun isNowOnAir(): Boolean = liveBroadcastContent == BroadcastType.LIVE
    fun isUpcoming(): Boolean = liveBroadcastContent == BroadcastType.UPCOMING

    data class Id(override val value: String) : YouTubeId

    enum class BroadcastType { LIVE, UPCOMING, NONE, }

    companion object {
        val YouTubeVideo.url: String get() = "https://youtube.com/watch?v=${id.value}"
        val YouTubeVideo.isArchived: Boolean
            get() = liveBroadcastContent != null && (!isLiveStream() || actualEndDateTime != null)
        internal val YouTubeVideo.isFreeChatTitle: Boolean
            get() = regex.any { title.contains(it) }
        private val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )

        fun YouTubeVideo.extend(old: YouTubeVideoExtended?): YouTubeVideoExtended = when (this) {
            is YouTubeVideoExtended -> this
            else -> YouTubeVideoExtendedImpl(old = old, video = this, null)
        }

        fun YouTubeVideo.extendAsFreeChat(): YouTubeVideoExtended =
            YouTubeVideoExtendedImpl(old = null, video = this, true)
    }
}

interface YouTubeVideoExtended : YouTubeVideo {
    val isFreeChat: Boolean?

    companion object {
        val YouTubeVideoExtended.isThumbnailUpdatable: Boolean
            get() {
                return if (this !is YouTubeVideoExtendedImpl) {
                    false
                } else {
                    val o = old ?: return false
                    isLiveStream() && (o.title != title || (o.isUpcoming() && isNowOnAir()))
                }
            }
    }
}

private class YouTubeVideoExtendedImpl(
    val old: YouTubeVideoExtended?,
    video: YouTubeVideo,
    private val _isFreeChat: Boolean?,
) : YouTubeVideoExtended, YouTubeVideo by video {
    override val isFreeChat: Boolean
        get() = _isFreeChat
            ?: if (old?.title == title) {
                isUpcoming() && old.isFreeChat ?: isFreeChatTitle
            } else {
                isUpcoming() && isFreeChatTitle
            }
}
