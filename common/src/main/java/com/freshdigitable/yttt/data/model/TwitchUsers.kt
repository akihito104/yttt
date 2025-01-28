package com.freshdigitable.yttt.data.model

import java.time.Duration
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

interface TwitchFollowings {
    val followerId: TwitchUser.Id
    val followings: List<TwitchBroadcaster>
    val updatableAt: Instant

    companion object {
        internal val MAX_AGE_BROADCASTER: Duration = Duration.ofHours(12)
        fun create(
            follower: TwitchUser.Id,
            followings: List<TwitchBroadcaster>,
            updatableAt: Instant,
        ): TwitchFollowings = Impl(follower, followings, updatableAt)

        fun createAtFetched(
            follower: TwitchUser.Id,
            followings: List<TwitchBroadcaster>,
            fetchedAt: Instant,
        ): TwitchFollowings = Impl(follower, followings, fetchedAt + MAX_AGE_BROADCASTER)

        fun getRemovedFollowingIds(
            old: TwitchFollowings,
            new: TwitchFollowings,
        ): Set<TwitchUser.Id> {
            require(old.updatableAt < new.updatableAt) {
                "old.updatableAt: ${old.updatableAt}, new.updatableAt: ${new.updatableAt}"
            }
            require(old.followerId == new.followerId) { "followerId must be same." }
            val removed = old.followings.map { it.id } - new.followings.map { it.id }.toSet()
            return removed.toSet()
        }
    }

    private data class Impl(
        override val followerId: TwitchUser.Id,
        override val followings: List<TwitchBroadcaster>,
        override val updatableAt: Instant,
    ) : TwitchFollowings
}
