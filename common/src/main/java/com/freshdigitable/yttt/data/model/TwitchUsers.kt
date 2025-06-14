package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

interface TwitchUser {
    val id: Id
    val loginName: String
    val displayName: String

    data class Id(override val value: String) : TwitchId
}

interface TwitchUserDetail : TwitchUser, Updatable {
    val description: String
    val profileImageUrl: String
    val createdAt: Instant

    companion object {
        val MAX_AGE_USER_DETAIL: Duration = Duration.ofDays(1)
        fun TwitchUserDetail.update(maxAge: Duration): TwitchUserDetail =
            object : TwitchUserDetail by this {
                override val maxAge: Duration? get() = maxAge
            }
    }
}

interface TwitchBroadcaster : TwitchUser {
    val followedAt: Instant
}

interface TwitchFollowings : Updatable {
    val followerId: TwitchUser.Id
    val followings: List<TwitchBroadcaster>
    override val maxAge: Duration get() = MAX_AGE_BROADCASTER

    companion object {
        internal val MAX_AGE_BROADCASTER: Duration = Duration.ofHours(12)
        fun create(
            follower: TwitchUser.Id,
            followings: List<TwitchBroadcaster>,
            fetchedAt: Instant?,
            maxAge: Duration? = null,
        ): TwitchFollowings = Impl(follower, followings, fetchedAt, maxAge ?: MAX_AGE_BROADCASTER)

        fun TwitchFollowings.update(new: TwitchFollowings): Updated {
            require(this.fetchedAt?.let { it < checkNotNull(new.fetchedAt) } != false) {
                "old.updatableAt: ${this.fetchedAt}, new.updatableAt: ${new.fetchedAt}"
            }
            require(this.followerId == new.followerId) { "followerId must be same." }
            return object : Updated, TwitchFollowings by new {
                override val removed: Set<TwitchUser.Id>
                    get() = getRemovedFollowingIds(this@update, new)
            }
        }

        private fun getRemovedFollowingIds(
            old: TwitchFollowings,
            new: TwitchFollowings,
        ): Set<TwitchUser.Id> {
            val removed = old.followings.map { it.id } - new.followings.map { it.id }.toSet()
            return removed.toSet()
        }
    }

    private data class Impl(
        override val followerId: TwitchUser.Id,
        override val followings: List<TwitchBroadcaster>,
        override val fetchedAt: Instant?,
        override val maxAge: Duration,
    ) : TwitchFollowings

    interface Updated : TwitchFollowings {
        val removed: Set<TwitchUser.Id>
    }
}
