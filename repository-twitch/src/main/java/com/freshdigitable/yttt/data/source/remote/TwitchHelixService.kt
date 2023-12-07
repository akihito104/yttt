package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.Broadcaster
import com.freshdigitable.yttt.data.model.ChannelStreamSchedule
import com.freshdigitable.yttt.data.model.FollowingStream
import com.freshdigitable.yttt.data.model.TwitchUserDetailRemote
import com.freshdigitable.yttt.data.model.TwitchVideoRemote
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant

internal interface TwitchHelixService {
    companion object {
        fun TwitchHelixService.getMe(): Call<TwitchUserResponse> = getUser()
    }

    @GET("helix/users")
    fun getUser(
        @Query("id") id: Collection<String>? = null,
        @Query("login") loginName: Collection<String>? = null,
    ): Call<TwitchUserResponse>

    @GET("helix/channels/followed")
    fun getFollowing(
        @Query("user_id") userId: String,
        @Query("broadcaster_id") broadcasterId: String? = null,
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") cursor: String? = null,
    ): Call<FollowedChannelsResponse>

    @GET("helix/streams/followed")
    fun getFollowedStreams(
        @Query("user_id") userId: String,
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") cursor: String? = null,
    ): Call<FollowingStreamsResponse>

    /// https://dev.twitch.tv/docs/api/reference/#get-channel-stream-schedule
    @GET("helix/schedule")
    fun getChannelStreamSchedule(
        @Query("broadcaster_id") broadcasterId: String,
        @Query("id") segmentId: String? = null,
        // The UTC date and time
        // If not specified, the request returns segments starting after the current UTC date and time.
        // Specify the date and time in RFC3339 format (for example, 2022-09-01T00:00:00Z).
        @Query("start_time") startTime: Instant? = null,
        // The maximum number of items to return per page in the response.
        // The minimum page size is 1 item per page and the maximum is 25 items per page.
        // The default is 20.
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") cursor: String? = null,
    ): Call<ChannelStreamScheduleResponse>

    // https://dev.twitch.tv/docs/api/reference/#get-videos
    @GET("helix/videos")
    fun getVideoByUserId(
        @Query("user_id") userId: String,
        @Query("language") language: String? = null,
        @Query("period") period: String? = null,
        @Query("sort") sort: String? = null,
        @Query("type") type: String? = null,
        // The maximum number of items to return per page in the response.
        // The minimum page size is 1 item per page and the maximum is 100. The default is 20.
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") nextCursor: String? = null,
        @Query("before") prevCursor: String? = null,
    ): Call<TwitchVideosResponse>
}

internal class TwitchUserResponse(@SerializedName("data") val data: List<TwitchUserDetailRemote>)

internal class FollowedChannelsResponse(
    @SerializedName("data")
    val data: Array<Broadcaster>,
    @SerializedName("pagination")
    override val pagination: Pagination,
    @SerializedName("total")
    val total: Int,
) : Pageable<Broadcaster> {
    override fun getItems(): Array<Broadcaster> = data
}

internal class Pagination(@SerializedName("cursor") val cursor: String?)
internal interface Pageable<T> {
    val pagination: Pagination
    fun getItems(): Array<T>
}

internal class FollowingStreamsResponse(
    @SerializedName("data")
    val data: Array<FollowingStream>,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<FollowingStream> {
    override fun getItems(): Array<FollowingStream> = data
}

internal class ChannelStreamScheduleResponse(
    @SerializedName("data")
    val data: ChannelStreamSchedule,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<ChannelStreamSchedule> {
    override fun getItems(): Array<ChannelStreamSchedule> = arrayOf(data)
}

internal class TwitchVideosResponse(
    @SerializedName("data")
    val data: Array<TwitchVideoRemote>,
    @SerializedName("pagination")
    val pagination: Pagination,
)
