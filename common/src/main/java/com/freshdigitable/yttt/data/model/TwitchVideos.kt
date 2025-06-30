package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.checkUpdatableBy
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import java.time.Duration
import java.time.Instant

interface TwitchVideo<T : TwitchVideo.TwitchVideoId> {
    val id: T
    val user: TwitchUser
    val title: String
    val url: String
    val thumbnailUrlBase: String
    val viewCount: Int
    val language: String

    interface TwitchVideoId : TwitchId
    data class Id(override val value: String) : TwitchVideoId

    fun getThumbnailUrl(width: Int = 640, height: Int = 360): String =
        thumbnailUrlBase.replace("{width}", "$width").replace("{height}", "$height")
}

interface TwitchVideoDetail : TwitchVideo<TwitchVideo.Id> {
    val description: String
    val createdAt: Instant
    val publishedAt: Instant
    val viewable: String
    val streamId: TwitchStream.Id?
    val type: String
    val duration: String
    val mutedSegments: List<MutedSegment>

    // A URL to a thumbnail image of the video. Before using the URL, you must replace the
    // %{width} and %{height} placeholders with the width and height of the thumbnail you want returned.
    // Due to current limitations, ${width} must be 320 and ${height} must be 180.
    override fun getThumbnailUrl(width: Int, height: Int): String =
        thumbnailUrlBase.replace("%{width}", "320").replace("%{height}", "180")

    interface MutedSegment {
        val duration: Int // [sec.]
        val offset: Int // [sec.]
    }
}

interface TwitchStream : TwitchVideo<TwitchStream.Id> {
    val gameId: TwitchCategory.Id
    val gameName: String
    val type: String
    val startedAt: Instant
    val tags: List<String>
    val isMature: Boolean
    override val url: String
        get() = "https://twitch.tv/${user.loginName}"

    data class Id(override val value: String) : TwitchVideo.TwitchVideoId
}

interface TwitchChannelSchedule {
    val segments: List<Stream>?
    val broadcaster: TwitchUser
    val vacation: Vacation?

    interface Stream {
        val id: Id
        val startTime: Instant
        val endTime: Instant?
        val title: String
        val canceledUntil: String?
        val category: TwitchCategory?
        val isRecurring: Boolean

        data class Id(override val value: String) : TwitchVideo.TwitchVideoId
    }

    interface Vacation {
        val startTime: Instant
        val endTime: Instant
    }

    companion object {
        val MAX_AGE_CHANNEL_SCHEDULE: Duration = Duration.ofDays(1)
    }
}

interface TwitchCategory {
    val id: Id
    val name: String
    val artUrlBase: String? get() = null
    val igdbId: String? get() = null

    data class Id(override val value: String) : TwitchId
}

interface TwitchStreams {
    val followerId: TwitchUser.Id
    val streams: List<TwitchStream>

    companion object {
        fun create(
            followerId: TwitchUser.Id,
            streams: List<TwitchStream>,
            cacheControl: CacheControl?,
        ): Updatable<TwitchStreams> = Impl(followerId, streams)
            .toUpdatable(cacheControl ?: CacheControl.empty())

        private val MAX_AGE_STREAM = Duration.ofMinutes(10)

        private fun TwitchStreams.update(new: TwitchStreams): TwitchStreams {
            require(this.followerId == new.followerId)
            val map = this.streams.associateBy { it.id }
            return object : Updated, TwitchStreams by new {
                override val updatableThumbnails: Set<String>
                    get() {
                        return new.streams.filter { n ->
                            val o = map[n.id] ?: return@filter true
                            n.mayUpdateThumbnail(o)
                        }.map { it.getThumbnailUrl() }.toSet()
                    }
                override val deletedThumbnails: Set<String>
                    get() {
                        val deleted = map.keys - new.streams.map { it.id }.toSet()
                        return deleted.mapNotNull { map[it]?.getThumbnailUrl() }.toSet()
                    }
            }
        }

        fun Updatable<TwitchStreams>.update(new: Updatable<TwitchStreams>): Updatable<TwitchStreams> {
            this.checkUpdatableBy(new)
            return this.item.update(new.item)
                .toUpdatable(new.cacheControl.overrideMaxAge(MAX_AGE_STREAM))
        }

        private fun TwitchStream.mayUpdateThumbnail(other: TwitchStream): Boolean =
            other.startedAt != startedAt || other.title != title || other.gameId != gameId
    }

    private class Impl(
        override val followerId: TwitchUser.Id,
        override val streams: List<TwitchStream>,
    ) : TwitchStreams

    interface Updated : TwitchStreams {
        val updatableThumbnails: Set<String>
        val deletedThumbnails: Set<String>
    }
}
