package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant
import kotlin.reflect.KClass

interface YouTubeChannel {
    val id: Id
    val title: String
    val iconUrl: String

    data class Id(override val value: String) : YouTubeId
}

data class YouTubeChannelEntity(
    override val id: YouTubeChannel.Id,
    override val title: String,
    override val iconUrl: String,
) : YouTubeChannel

interface YouTubeChannelAddition {
    val bannerUrl: String?
    val subscriberCount: BigInteger
    val isSubscriberHidden: Boolean
    val videoCount: BigInteger
    val viewsCount: BigInteger
    val publishedAt: Instant
    val customUrl: String
    val keywords: Collection<String>
    val description: String?
    val uploadedPlayList: YouTubePlaylist.Id?
}

interface YouTubeChannelDetail : YouTubeChannel, YouTubeChannelAddition
interface YouTubeChannelSection : Comparable<YouTubeChannelSection> {
    val id: Id
    val channelId: YouTubeChannel.Id
    val title: String?
    val position: Long
    val type: Type
    val content: Content<*>?

    override fun compareTo(other: YouTubeChannelSection): Int = (position - other.position).toInt()

    data class Id(override val value: String) : YouTubeId

    enum class Type(val metaType: KClass<out Content<*>>) {
        ALL_PLAYLIST(Content.Playlist::class), COMPLETED_EVENT(Content.Playlist::class),
        LIVE_EVENT(Content.Playlist::class), MULTIPLE_CHANNEL(Content.Channels::class),
        MULTIPLE_PLAYLIST(Content.Playlist::class),
        POPULAR_UPLOAD(Content.Playlist::class), RECENT_UPLOAD(Content.Playlist::class),
        SINGLE_PLAYLIST(Content.Playlist::class), SUBSCRIPTION(Content.Channels::class),
        UPCOMING_EVENT(Content.Playlist::class),
        UNDEFINED(Content.Playlist::class),
    }

    interface Content<T> {
        data class Playlist(override val item: List<YouTubePlaylist.Id>) :
            Content<YouTubePlaylist.Id>
        data class Channels(override val item: List<YouTubeChannel.Id>) : Content<YouTubeChannel.Id>

        val item: List<T>
    }
}
