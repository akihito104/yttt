package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant
import kotlin.reflect.KClass

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String
    val platform: LivePlatform

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(
        override val value: String,
        override val type: KClass<out IdBase>,
    ) : LiveId
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
    override val platform: LivePlatform,
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
    val uploadedPlayList: YouTubePlaylist.Id?
}

interface LiveChannelDetail : LiveChannel, LiveChannelAddition

data class LiveChannelDetailEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
    override val platform: LivePlatform,
    override val bannerUrl: String?,
    override val subscriberCount: BigInteger,
    override val isSubscriberHidden: Boolean,
    override val videoCount: BigInteger,
    override val viewsCount: BigInteger,
    override val publishedAt: Instant,
    override val customUrl: String,
    override val keywords: Collection<String>,
    override val description: String?,
    override val uploadedPlayList: YouTubePlaylist.Id?,
) : LiveChannelDetail
