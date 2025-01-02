package com.freshdigitable.yttt.data.model

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
        private val YouTubeVideo.isFreeChatTitle: Boolean
            get() = regex.any { title.contains(it) }
        private val regex = listOf(
            "free chat".toRegex(RegexOption.IGNORE_CASE),
            "フリーチャット".toRegex(),
            "ふりーちゃっと".toRegex(),
            "schedule".toRegex(RegexOption.IGNORE_CASE),
            "の予定".toRegex(),
        )

        fun YouTubeVideo.extend(old: YouTubeVideoExtended?): YouTubeVideoExtended {
            return when (this) {
                is YouTubeVideoExtended -> this
                else -> {
                    val isFreeChat = if (old?.title == title) {
                        isUpcoming() && old.isFreeChat ?: isFreeChatTitle
                    } else {
                        isUpcoming() && isFreeChatTitle
                    }
                    YouTubeVideoExtendedImpl(this, isFreeChat)
                }
            }
        }

        fun YouTubeVideo.extendAsFreeChat(): YouTubeVideoExtended =
            YouTubeVideoExtendedImpl(this, true)
    }

    interface IsFreeChat {
        val isFreeChat: Boolean?
    }
}

interface YouTubeVideoExtended : YouTubeVideo, YouTubeVideo.IsFreeChat
private class YouTubeVideoExtendedImpl(
    private val video: YouTubeVideo,
    override val isFreeChat: Boolean,
) : YouTubeVideoExtended, YouTubeVideo by video
