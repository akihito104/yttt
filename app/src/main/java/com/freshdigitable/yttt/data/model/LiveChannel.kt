package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant

interface LiveChannel {
    val id: Id
    val title: String
    val iconUrl: String

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    data class Id(override val value: String) : IdBase<String>
}

data class LiveChannelEntity(
    override val id: LiveChannel.Id,
    override val title: String,
    override val iconUrl: String,
) : LiveChannel

interface IdBase<S> {
    val value: S
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

interface LiveChannelSection {
    val channelId: LiveChannel.Id
    val title: String
    val position: Long
}

interface LiveChannelDetail : LiveChannel {
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
