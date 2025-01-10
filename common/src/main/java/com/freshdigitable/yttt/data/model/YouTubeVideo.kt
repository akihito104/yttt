package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isFreeChatTitle
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isUnscheduledLive
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable.Companion.NOT_UPDATABLE
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable.Companion.UPDATABLE_DURATION_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable.Companion.UPDATABLE_DURATION_FREE_CHAT
import com.freshdigitable.yttt.data.model.YouTubeVideoUpdatable.Companion.UPDATABLE_DURATION_ON_AIR
import java.math.BigInteger
import java.time.Duration
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

        fun YouTubeVideo.isUnscheduledLive(): Boolean =
            isUpcoming() && scheduledStartDateTime == null

        fun YouTubeVideo.extend(
            old: YouTubeVideoExtended?,
            isFreeChat: Boolean? = null,
            fetchedAt: Instant,
        ): YouTubeVideoExtended = when (this) {
            is YouTubeVideoExtended -> this
            else -> YouTubeVideoExtendedImpl(old = old, video = this, isFreeChat, fetchedAt)
        }
    }
}

interface YouTubeVideoExtended : YouTubeVideo, YouTubeVideoUpdatable {
    val isFreeChat: Boolean?

    companion object {
        fun YouTubeVideoExtended.asFreeChat(): YouTubeVideoExtended = when (this) {
            is YouTubeVideoExtendedImpl -> YouTubeVideoExtendedImpl(
                old = old,
                video = this,
                _isFreeChat = true,
                fetchedAt = fetchedAt,
            )

            else -> object : YouTubeVideoExtended by this {
                override val isFreeChat: Boolean = true
            }
        }

        val YouTubeVideoExtended.isThumbnailUpdatable: Boolean
            get() {
                val o = (this as? YouTubeVideoExtendedImpl)?.old ?: return false
                return isLiveStream() && (o.title != title || (o.isUpcoming() && isNowOnAir()))
            }
    }
}

interface YouTubeVideoUpdatable {
    val updatableAt: Instant
    fun isUpdatable(current: Instant): Boolean = updatableAt <= current

    companion object {
        /**
         * archived video is not needed to update (`Long.MAX_VALUE`, because of DB limitation :( )
         */
        internal val NOT_UPDATABLE: Instant = Instant.ofEpochMilli(Long.MAX_VALUE)

        /**
         * update duration for default (20 min.)
         */
        val UPDATABLE_DURATION_DEFAULT: Duration = Duration.ofMinutes(20)

        /**
         * update duration for free chat (1 day)
         */
        val UPDATABLE_DURATION_FREE_CHAT: Duration = Duration.ofDays(1)

        /**
         * update duration for on air stream (5 min.)
         */
        internal val UPDATABLE_DURATION_ON_AIR = Duration.ofMinutes(5)
    }
}

private class YouTubeVideoExtendedImpl(
    val old: YouTubeVideoExtended?,
    video: YouTubeVideo,
    private val _isFreeChat: Boolean?,
    val fetchedAt: Instant,
) : YouTubeVideoExtended, YouTubeVideo by video {
    override val isFreeChat: Boolean
        get() = _isFreeChat
            ?: if (old?.title == title) {
                isUpcoming() && old.isFreeChat ?: isFreeChatTitle
            } else {
                isUpcoming() && isFreeChatTitle
            }
    override val updatableAt: Instant
        get() {
            val defaultValue = fetchedAt + UPDATABLE_DURATION_DEFAULT
            val expiring = when {
                isFreeChat -> fetchedAt + UPDATABLE_DURATION_FREE_CHAT
                isUnscheduledLive() -> defaultValue
                isUpcoming() -> defaultValue.coerceAtMost(checkNotNull(scheduledStartDateTime))
                isNowOnAir() -> fetchedAt + UPDATABLE_DURATION_ON_AIR
                isArchived -> NOT_UPDATABLE
                else -> defaultValue
            }
            return expiring
        }
}
