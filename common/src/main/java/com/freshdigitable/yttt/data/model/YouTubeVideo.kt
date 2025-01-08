package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isFreeChatTitle
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isUnscheduledLive
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable.Companion.NOT_UPDATABLE
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable.Companion.UPDATABLE_DURATION_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable.Companion.UPDATABLE_DURATION_FREE_CHAT
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable.Companion.UPDATABLE_DURATION_ON_AIR
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

        fun YouTubeVideo.isUnscheduledLive(): Boolean =
            isUpcoming() && scheduledStartDateTime == null

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

fun YouTubeVideoExtended.updatable(fetchedAt: Instant): YouTubeVideoExtendedUpdatable =
    when (this) {
        is YouTubeVideoExtendedUpdatable -> this
        else -> YouTubeVideoExtendedUpdatableImpl(this, fetchedAt)
    }

interface YouTubeVideoExtendedUpdatable : YouTubeVideoExtended {
    val updatableAt: Instant

    companion object {
        /**
         * archived video is not needed to update (`Long.MAX_VALUE`, because of DB limitation :( )
         */
        internal val NOT_UPDATABLE: Instant = Instant.ofEpochMilli(Long.MAX_VALUE)

        /**
         * update duration for default (20 min.)
         */
        internal val UPDATABLE_DURATION_DEFAULT = Duration.ofMinutes(20)

        /**
         * update duration for free chat (1 day)
         */
        internal val UPDATABLE_DURATION_FREE_CHAT = Duration.ofDays(1)

        /**
         * update duration for on air stream (5 min.)
         */
        internal val UPDATABLE_DURATION_ON_AIR = Duration.ofMinutes(5)
    }
}

private class YouTubeVideoExtendedUpdatableImpl(
    video: YouTubeVideoExtended,
    private val fetchedAt: Instant,
) : YouTubeVideoExtendedUpdatable, YouTubeVideoExtended by video {
    override val updatableAt: Instant
        get() {
            val defaultValue = fetchedAt + UPDATABLE_DURATION_DEFAULT
            val expiring = when {
                isFreeChat == true -> fetchedAt + UPDATABLE_DURATION_FREE_CHAT
                isUnscheduledLive() -> defaultValue
                isUpcoming() -> defaultValue.coerceAtMost(checkNotNull(scheduledStartDateTime))
                isNowOnAir() -> fetchedAt + UPDATABLE_DURATION_ON_AIR
                isArchived -> NOT_UPDATABLE
                else -> defaultValue
            }
            return expiring
        }
}
