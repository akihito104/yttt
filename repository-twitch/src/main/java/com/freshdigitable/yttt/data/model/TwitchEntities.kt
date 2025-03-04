package com.freshdigitable.yttt.data.model

import com.google.gson.annotations.SerializedName
import java.time.Instant

internal class TwitchUserDetailRemote(
    @SerializedName("id")
    override val id: TwitchUser.Id,
    @SerializedName("display_name")
    override val displayName: String,
    @SerializedName("profile_image_url")
    override val profileImageUrl: String,
    /**
     * NOTE: This field has been deprecate (see [Get Users API endpoint – “view_count” deprecation](https://discuss.dev.twitch.tv/t/get-users-api-endpoint-view-count-deprecation/37777)).
     * Any data in this field is not valid and should not be used.
     */
//    @SerializedName("view_count")
//    override val viewsCount: Int,
    @SerializedName("created_at")
    override val createdAt: Instant,
    @SerializedName("login")
    override val loginName: String,
    @SerializedName("description")
    override val description: String,
) : TwitchUserDetail

internal class TwitchUserRemote(
    override val id: TwitchUser.Id,
    override val loginName: String,
    override val displayName: String
) : TwitchUser

internal class Broadcaster(
    @SerializedName("broadcaster_id")
    override val id: TwitchUser.Id,
    @SerializedName("broadcaster_login")
    override val loginName: String,
    @SerializedName("broadcaster_name")
    override val displayName: String,
    @SerializedName("followed_at")
    override val followedAt: Instant,
) : TwitchBroadcaster

internal data class FollowingStream(
    @SerializedName("id")
    override val id: TwitchStream.Id,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("user_login")
    val loginName: String,
    @SerializedName("user_name")
    val displayName: String,
    @SerializedName("game_id")
    override val gameId: String,
    @SerializedName("game_name")
    override val gameName: String,
    @SerializedName("type")
    override val type: String,
    @SerializedName("title")
    override val title: String,
    @SerializedName("viewer_count")
    override val viewCount: Int,
    @SerializedName("started_at")
    override val startedAt: Instant,
    @SerializedName("language")
    override val language: String,
    @SerializedName("thumbnail_url")
    override val thumbnailUrlBase: String,
    @SerializedName("tags")
    override val tags: List<String>,
    @SerializedName("is_mature")
    override val isMature: Boolean,
) : TwitchStream {
    override val user: TwitchUser
        get() = TwitchUserRemote(
            TwitchUser.Id(userId), loginName = loginName, displayName = displayName
        )
}

internal class ChannelStreamSchedule(
    @SerializedName("segments")
    override val segments: List<StreamScheduleRemote>?,
    @SerializedName("broadcaster_id")
    val broadcasterId: String,
    @SerializedName("broadcaster_name")
    val broadcasterName: String,
    @SerializedName("broadcaster_login")
    val broadcasterLogin: String,
    @SerializedName("vacation")
    override val vacation: VacationRemote?,
) : TwitchChannelSchedule {
    class StreamScheduleRemote(
        @SerializedName("id")
        override val id: TwitchChannelSchedule.Stream.Id,
        @SerializedName("start_time")
        override val startTime: Instant,
        @SerializedName("end_time")
        override val endTime: Instant?,
        @SerializedName("title")
        override val title: String,
        @SerializedName("canceled_until")
        override val canceledUntil: String?,
        @SerializedName("category")
        override val category: StreamCategoryRemote?,
        @SerializedName("is_recurring")
        override val isRecurring: Boolean,
    ) : TwitchChannelSchedule.Stream

    class StreamCategoryRemote(
        @SerializedName("id")
        override val id: String,
        @SerializedName("name")
        override val name: String,
    ) : TwitchChannelSchedule.StreamCategory

    class VacationRemote(
        @SerializedName("start_time")
        override val startTime: Instant,
        @SerializedName("end_time")
        override val endTime: Instant,
    ) : TwitchChannelSchedule.Vacation

    override val broadcaster: TwitchUser
        get() = TwitchUserRemote(
            TwitchUser.Id(broadcasterId),
            loginName = broadcasterLogin,
            displayName = broadcasterName,
        )
}

internal data class TwitchVideoRemote(
    @SerializedName("id")
    override val id: TwitchVideo.Id,
    @SerializedName("stream_id")
    override val streamId: TwitchStream.Id?,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("user_login")
    val userLoginName: String,
    @SerializedName("user_name")
    val userDisplayName: String,
    @SerializedName("title")
    override val title: String,
    @SerializedName("description")
    override val description: String,
    @SerializedName("created_at")
    override val createdAt: Instant,
    @SerializedName("published_at")
    override val publishedAt: Instant,
    @SerializedName("url")
    override val url: String,
    @SerializedName("thumbnail_url")
    override val thumbnailUrlBase: String,
    @SerializedName("viewable")
    override val viewable: String,
    @SerializedName("view_count")
    override val viewCount: Int,
    @SerializedName("language")
    override val language: String,
    @SerializedName("type")
    override val type: String,
    @SerializedName("duration")
    override val duration: String,
    @SerializedName("muted_segments")
    override val mutedSegments: List<MutedSegmentRemote>,
) : TwitchVideoDetail {
    override val user: TwitchUser = object : TwitchUser {
        override val id: TwitchUser.Id
            get() = TwitchUser.Id(userId)
        override val loginName: String
            get() = userLoginName
        override val displayName: String
            get() = userDisplayName
    }

    class MutedSegmentRemote(
        @SerializedName("duration")
        override val duration: Int, // [sec.]
        @SerializedName("offset")
        override val offset: Int, // [sec.]
    ) : TwitchVideoDetail.MutedSegment
}
