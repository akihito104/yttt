package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.MAX_AGE_FREE_CHAT
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.MAX_AGE_NOT_UPDATABLE
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.MAX_AGE_ON_AIR
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.UPCOMING_DEADLINE
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isArchived
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isFreeChatTitle
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isPostponedLive
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.isUnscheduledLive
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedImpl.Companion.createAsFreeChat
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

interface YouTubeVideo : Updatable {
    val id: Id
    val title: String
    val channel: YouTubeChannelTitle
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
        internal val UPCOMING_DEADLINE: Duration = Duration.ofHours(6)
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

        fun YouTubeVideo.isPostponedLive(current: Instant): Boolean {
            val s = scheduledStartDateTime ?: return false
            return isUpcoming() && s <= current
                && Duration.between(s, current) > MAX_AGE_LIMIT_SOON
        }

        fun YouTubeVideo.extend(
            old: YouTubeVideoExtended?,
            isFreeChat: Boolean? = null,
        ): YouTubeVideoExtended = when (this) {
            is YouTubeVideoExtended -> this
            else -> YouTubeVideoExtendedImpl(old = old, video = this, isFreeChat)
        }

        /**
         * archived video is not needed to update (`Long.MAX_VALUE`, because of DB limitation :( )
         */
        internal val MAX_AGE_NOT_UPDATABLE: Duration = Duration.ofMillis(Long.MAX_VALUE)

        /**
         * update duration for default (20 min.)
         */
        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(20)

        /**
         * update duration for free chat (1 day)
         */
        val MAX_AGE_FREE_CHAT: Duration = Duration.ofDays(1)

        /**
         * update duration for on air stream (5 min.)
         */
        internal val MAX_AGE_ON_AIR = Duration.ofMinutes(5)

        /**
         * updatable deadline as scheduled starting datetime (30 min.)
         */
        internal val MAX_AGE_LIMIT_SOON = Duration.ofMinutes(30)
    }
}

interface YouTubeVideoExtended : YouTubeVideo {
    override val channel: YouTubeChannel
    val isFreeChat: Boolean?
    val isThumbnailUpdatable: Boolean get() = false

    companion object {
        fun YouTubeVideoExtended.asFreeChat(): YouTubeVideoExtended = when (this) {
            is YouTubeVideoExtendedImpl -> this.createAsFreeChat()

            else -> object : YouTubeVideoExtended by this {
                override val isFreeChat: Boolean = true
            }
        }

        fun YouTubeVideoExtended.isUpcomingWithinPublicationDeadline(current: Instant): Boolean {
            check(isUpcoming())
            check(isFreeChat != true)
            return current <= (checkNotNull(scheduledStartDateTime) + UPCOMING_DEADLINE)
        }
    }
}

private class YouTubeVideoExtendedImpl(
    private val old: YouTubeVideoExtended?,
    private val video: YouTubeVideo,
    private val _isFreeChat: Boolean?,
) : YouTubeVideoExtended, YouTubeVideo by video {
    override val channel: YouTubeChannel
        get() = old?.channel?.update(video.channel) ?: video.channel.toChannel()
    override val isFreeChat: Boolean
        get() = isFreeChat(old, video, _isFreeChat)
    override val isThumbnailUpdatable: Boolean
        get() {
            val o = old ?: return false
            return when {
                isFreeChat -> o.isUpdatable(checkNotNull(video.cacheControl.fetchedAt)) // at same time of updating this entity
                isLiveStream() -> (o.title != title || (o.isUpcoming() && isNowOnAir()))
                else -> false
            }
        }
    override val cacheControl: CacheControl
        get() = video.cacheControl.overrideMaxAge(maxAge(old, video, _isFreeChat))

    companion object {
        fun YouTubeVideoExtendedImpl.createAsFreeChat(): YouTubeVideoExtended =
            YouTubeVideoExtendedImpl(
                old = this.old,
                video = this,
                _isFreeChat = true,
            )

        fun isFreeChat(
            old: YouTubeVideoExtended?,
            new: YouTubeVideo,
            isNewVideoFreeChat: Boolean?,
        ): Boolean = isNewVideoFreeChat ?: if (old?.title == new.title) {
            new.isUpcoming() && old.isFreeChat ?: new.isFreeChatTitle
        } else {
            new.isUpcoming() && new.isFreeChatTitle
        }

        fun maxAge(
            old: YouTubeVideoExtended?,
            new: YouTubeVideo,
            isNewVideoFreeChat: Boolean?,
        ): Duration {
            val defaultValue = MAX_AGE_DEFAULT
            val fetchedAt = checkNotNull(new.cacheControl.fetchedAt)
            return when {
                isFreeChat(old, new, isNewVideoFreeChat) -> MAX_AGE_FREE_CHAT
                new.isUnscheduledLive() -> defaultValue
                new.isPostponedLive(fetchedAt) -> defaultValue
                new.isUpcoming() -> Duration.between(
                    fetchedAt,
                    (fetchedAt + defaultValue).coerceAtMost(checkNotNull(new.scheduledStartDateTime)),
                )

                new.isNowOnAir() -> MAX_AGE_ON_AIR
                new.isArchived -> MAX_AGE_NOT_UPDATABLE
                else -> defaultValue
            }
        }

        private fun YouTubeChannelTitle.toChannel(): YouTubeChannel {
            if (this is YouTubeChannel) return this
            return YouTubeChannelEntity(id = id, title = title, iconUrl = "")
        }

        private fun YouTubeChannel.update(title: YouTubeChannelTitle): YouTubeChannel {
            val icon = if ((title as? YouTubeChannel)?.iconUrl.isNullOrEmpty()) {
                this.iconUrl
            } else {
                title.iconUrl
            }
            return YouTubeChannelEntity(id = id, title = title.title, iconUrl = icon)
        }
    }
}
