package com.freshdigitable.yttt.data.model

import java.math.BigInteger
import java.time.Instant

interface TwitchUser {
    val id: Id
    val loginName: String
    val displayName: String

    data class Id(override val value: String) : TwitchId
}

interface TwitchUserDetail : TwitchUser {
    val description: String
    val profileImageUrl: String
    val viewsCount: Int
    val createdAt: Instant
}

interface TwitchBroadcaster : TwitchUser {
    val followedAt: Instant
}

fun TwitchUserDetail.toLiveChannelDetail(): LiveChannelDetail = LiveChannelDetailEntity(
    id = LiveChannel.Id(id.value, id.platform),
    title = this.displayName,
    iconUrl = this.profileImageUrl,
    bannerUrl = "",
    customUrl = loginName,
    description = description,
    isSubscriberHidden = false,
    keywords = emptyList(),
    publishedAt = this.createdAt,
    subscriberCount = BigInteger.ZERO,
    uploadedPlayList = null,
    videoCount = BigInteger.ZERO,
    viewsCount = BigInteger.valueOf(this.viewsCount.toLong()),
)
