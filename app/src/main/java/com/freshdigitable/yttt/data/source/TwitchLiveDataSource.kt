package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LivePlatform
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TwitchLiveDataSource {
    val onAir: Flow<List<TwitchStream>>
    val upcoming: Flow<List<TwitchChannelSchedule>>
    suspend fun getAuthorizeUrl(): String
    suspend fun findUsersById(ids: Collection<TwitchUser.Id>? = null): List<TwitchUserDetail>
    suspend fun fetchMe(): TwitchUser?
    suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster>
    suspend fun fetchFollowedStreams(): List<TwitchStream>
    suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int = 10,
    ): List<TwitchChannelSchedule>

    suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<out TwitchVideo.TwitchVideoId>?
    suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int = 20,
    ): List<TwitchVideoDetail>
}

interface TwitchId : IdBase<String> {
    override val platform: LivePlatform get() = LivePlatform.TWITCH
}

inline fun <reified T : TwitchId> IdBase<String>.mapTo(): T {
    check(this.platform == LivePlatform.TWITCH)
    return when (T::class) {
        TwitchUser.Id::class -> TwitchUser.Id(value) as T
        TwitchStream.Id::class -> TwitchStream.Id(value) as T
        TwitchChannelSchedule.Stream.Id::class ->
            TwitchChannelSchedule.Stream.Id(value) as T

        TwitchVideo.Id::class -> TwitchVideo.Id(value) as T

        else -> throw AssertionError("unsupported id type: $this")
    }
}

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
        val endTime: Instant
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
    override val url: String = "https://twitch.tv/${user.loginName}"
    override val thumbnailUrlBase: String = ""
    override val viewCount: Int = 0
    override val language: String = ""

    override fun getThumbnailUrl(width: Int, height: Int): String = ""
}

fun TwitchChannelSchedule.toTwitchVideoList(): List<TwitchStreamSchedule> {
    return segments?.map { s -> TwitchStreamSchedule(user = broadcaster, schedule = s) }
        ?: emptyList()
}
