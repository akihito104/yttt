package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Duration
import java.time.Instant

interface YouTubeChannelBase {
    val id: YouTubeChannel.Id
}

interface YouTubeChannelTitle : YouTubeChannelBase {
    val title: String
}

interface YouTubeChannel : YouTubeChannelTitle {
    val iconUrl: String

    data class Id(override val value: String) : YouTubeId
}

data class YouTubeChannelEntity(
    override val id: YouTubeChannel.Id,
    override val title: String,
    override val iconUrl: String,
) : YouTubeChannel

interface YouTubeChannelRelatedPlaylist : YouTubeChannelBase {
    val uploadedPlayList: YouTubePlaylist.Id?
}

interface YouTubeChannelAddition {
    val publishedAt: Instant
    val customUrl: String

    val bannerUrl: String?
    val keywords: Collection<String>
    val description: String?

    val subscriberCount: BigInteger
    val isSubscriberHidden: Boolean
    val videoCount: BigInteger
    val viewsCount: BigInteger
}

interface YouTubeChannelDetail : YouTubeChannel, YouTubeChannelAddition,
    YouTubeChannelRelatedPlaylist {
    companion object {
        val MAX_AGE: Duration = Duration.ofDays(1)
    }
}

interface YouTubeChannelSection : Comparable<YouTubeChannelSection> {
    val id: Id
    val channelId: YouTubeChannel.Id
    val title: String?
    val position: Long
    val type: Type
    val content: Content<*>?

    override fun compareTo(other: YouTubeChannelSection): Int = (position - other.position).toInt()

    data class Id(override val value: String) : YouTubeId

    enum class Type {
        // has content
        SINGLE_PLAYLIST, MULTIPLE_PLAYLIST, MULTIPLE_CHANNEL,

        // content is null but can get from another api
        ALL_PLAYLIST, COMPLETED_EVENT, LIVE_EVENT, UPCOMING_EVENT, SUBSCRIPTION,

        // content is null and cannot get
        POPULAR_UPLOAD, RECENT_UPLOAD, UNDEFINED,
    }

    sealed interface Content<T> {
        data class Playlist(override val item: List<YouTubePlaylist.Id>) :
            Content<YouTubePlaylist.Id>

        data class Channels(override val item: List<YouTubeChannel.Id>) : Content<YouTubeChannel.Id>

        val item: List<T>
    }
}
