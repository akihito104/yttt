package com.freshdigitable.yttt.data.model

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

    fun getThumbnailUrl(width: Int = 1920, height: Int = 1080): String =
        thumbnailUrlBase.replace("{width}x{height}", "${width}x${height}")
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
    override fun getThumbnailUrl(width: Int, height: Int): String =
        thumbnailUrlBase.replace("%{width}", "$width").replace("%{height}", "$height")

    interface MutedSegment {
        val duration: Int // [sec.]
        val offset: Int // [sec.]
    }
}

interface TwitchStream : TwitchVideo<TwitchStream.Id> {
    val gameId: String
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
        val category: StreamCategory?
        val isRecurring: Boolean

        data class Id(override val value: String) : TwitchVideo.TwitchVideoId
    }

    interface StreamCategory {
        val id: String
        val name: String
    }

    interface Vacation {
        val startTime: Instant
        val endTime: Instant
    }
}

data class TwitchStreamSchedule(
    override val user: TwitchUser,
    val schedule: TwitchChannelSchedule.Stream,
) : TwitchVideo<TwitchChannelSchedule.Stream.Id> {
    override val id: TwitchChannelSchedule.Stream.Id get() = schedule.id
    override val title: String get() = schedule.title
    override val url: String get() = "https://twitch.tv/${user.loginName}/schedule?seriesID=${id.value}"
    override val thumbnailUrlBase: String = ""
    override val viewCount: Int = 0
    override val language: String = ""

    override fun getThumbnailUrl(width: Int, height: Int): String = ""
}

fun TwitchChannelSchedule.toTwitchVideoList(): List<TwitchStreamSchedule> {
    return segments?.map { s -> TwitchStreamSchedule(user = broadcaster, schedule = s) }
        ?: emptyList()
}
