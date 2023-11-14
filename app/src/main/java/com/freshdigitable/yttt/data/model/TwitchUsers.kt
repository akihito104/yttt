package com.freshdigitable.yttt.data.model

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
