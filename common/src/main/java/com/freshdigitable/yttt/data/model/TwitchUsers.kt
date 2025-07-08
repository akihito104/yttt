package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.checkUpdatableBy
import com.freshdigitable.yttt.data.model.Updatable.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
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
    val createdAt: Instant

    companion object {
        val MAX_AGE_USER_DETAIL: Duration = Duration.ofDays(1)
        fun Updatable<TwitchUserDetail>.update(maxAge: Duration): Updatable<TwitchUserDetail> =
            overrideMaxAge(maxAge)
    }
}

interface TwitchBroadcaster : TwitchUser {
    val followedAt: Instant
}

interface TwitchFollowings {
    val followerId: TwitchUser.Id
    val followings: List<TwitchBroadcaster>

    companion object {
        internal val MAX_AGE_BROADCASTER: Duration = Duration.ofHours(12)
        fun create(
            follower: TwitchUser.Id,
            followings: List<TwitchBroadcaster>,
            cacheControl: CacheControl?,
        ): Updatable<TwitchFollowings> = Impl(follower, followings)
            .toUpdatable(cacheControl ?: CacheControl.EMPTY)

        private fun TwitchFollowings.update(new: TwitchFollowings): Updated {
            require(this.followerId == new.followerId) { "followerId must be same." }
            return object : Updated, TwitchFollowings by new {
                override val removed: Set<TwitchUser.Id>
                    get() = getRemovedFollowingIds(this@update, new)
            }
        }

        fun Updatable<TwitchFollowings>.update(new: Updatable<TwitchFollowings>): Updatable<TwitchFollowings> {
            this.checkUpdatableBy(new)
            return this.item.update(new.item)
                .toUpdatable(new.cacheControl.overrideMaxAge(MAX_AGE_BROADCASTER))
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
    ) : TwitchFollowings

    interface Updated : TwitchFollowings {
        val removed: Set<TwitchUser.Id>
    }
}
