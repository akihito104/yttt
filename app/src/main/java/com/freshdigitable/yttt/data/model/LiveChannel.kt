package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant
import kotlin.reflect.KClass

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val platform: LivePlatform = LivePlatform.YOUTUBE
    ) : IdBase<String>
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
) : LiveChannel

interface LiveChannelAddition {
    val bannerUrl: String?
    val subscriberCount: BigInteger
    val isSubscriberHidden: Boolean
    val videoCount: BigInteger
    val viewsCount: BigInteger
    val publishedAt: Instant
    val customUrl: String
    val keywords: Collection<String>
    val description: String?
    val uploadedPlayList: LivePlaylist.Id?
}

interface LiveChannelDetail : LiveChannel, LiveChannelAddition

interface LiveChannelSection : Comparable<LiveChannelSection> {
    val id: Id
    val channelId: LiveChannel.Id
    val title: String?
    val position: Long
    val type: Type
    val content: Content<*>?

    override fun compareTo(other: LiveChannelSection): Int = (position - other.position).toInt()

    data class Id(override val value: String) : IdBase<String> {
        override val platform: LivePlatform = LivePlatform.YOUTUBE
    }

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
        data class Playlist(override val item: List<LivePlaylist.Id>) : Content<LivePlaylist.Id>
        data class Channels(override val item: List<LiveChannel.Id>) : Content<LiveChannel.Id>

        val item: List<T>
    }
}

data class LiveChannelDetailEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
    override val bannerUrl: String?,
    override val subscriberCount: BigInteger,
    override val isSubscriberHidden: Boolean,
    override val videoCount: BigInteger,
    override val viewsCount: BigInteger,
    override val publishedAt: Instant,
    override val customUrl: String,
    override val keywords: Collection<String>,
    override val description: String?,
    override val uploadedPlayList: LivePlaylist.Id?
) : LiveChannelDetail
