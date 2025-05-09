package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.reflect.Type
import java.time.Instant

internal interface TwitchHelixService {
    companion object {
        internal const val BASE_URL = "https://api.twitch.tv/"
        fun TwitchHelixService.getMe(): Call<TwitchUserResponse> = getUser()
    }

    @GET("helix/users")
    fun getUser(
        @Query("id") id: Collection<TwitchUser.Id>? = null,
        @Query("login") loginName: Collection<String>? = null,
    ): Call<TwitchUserResponse>

    @GET("helix/channels/followed")
    fun getFollowing(
        @Query("user_id") userId: TwitchUser.Id,
        @Query("broadcaster_id") broadcasterId: TwitchUser.Id? = null,
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") cursor: String? = null,
    ): Call<FollowedChannelsResponse>

    @GET("helix/streams/followed")
    fun getFollowedStreams(
        @Query("user_id") userId: TwitchUser.Id,
        @Query("first") itemsPerPage: Int? = null,
        @Query("after") cursor: String? = null,
    ): Call<FollowingStreamsResponse>

    /// https://dev.twitch.tv/docs/api/reference/#get-channel-stream-schedule
    @GET("helix/schedule")
    fun getChannelStreamSchedule(
        @Query("broadcaster_id") broadcasterId: TwitchUser.Id,
        @Query("id") segmentId: TwitchChannelSchedule.Stream.Id? = null,
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
        @Query("user_id") userId: TwitchUser.Id,
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

    // https://dev.twitch.tv/docs/api/reference/#get-games
    @GET("helix/games")
    fun getGame(@Query("id") id: Set<TwitchCategory.Id>): Call<TwitchGameResponse>
}

internal class TwitchUserResponse(
    @SerializedName("data") override val item: List<TwitchUserDetailRemote>,
) : NetworkResponse<List<TwitchUserDetail>>

internal class FollowedChannelsResponse(
    @SerializedName("data")
    override val item: List<Broadcaster>,
    @SerializedName("pagination")
    override val pagination: Pagination,
    @SerializedName("total")
    val total: Int,
) : Pageable<List<TwitchBroadcaster>>

internal class Pagination(@SerializedName("cursor") val cursor: String?)
internal interface Pageable<T> : NetworkResponse<T> {
    val pagination: Pagination
    override val item: T
    override val nextPageToken: String? get() = pagination.cursor
}

internal class FollowingStreamsResponse(
    @SerializedName("data")
    override val item: List<FollowingStream>,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<List<TwitchStream>>

internal class ChannelStreamScheduleResponse(
    @SerializedName("data")
    override val item: ChannelStreamSchedule,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<TwitchChannelSchedule>

internal class TwitchVideosResponse(
    @SerializedName("data")
    override val item: List<TwitchVideoRemote>,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<List<TwitchVideoDetail>>

internal class TwitchGameResponse(
    @SerializedName("data") override val item: List<TwitchGameRemote>,
) : NetworkResponse<List<TwitchCategory>>

internal class IdConverterFactory : Converter.Factory() {
    companion object {
        private val twitchIdConverter = Converter<TwitchId, String> { it.value }
        private val twitchIdTypes = setOf(
            TwitchUser.Id::class.java,
            TwitchCategory.Id::class.java,
            TwitchChannelSchedule.Stream.Id::class.java,
        )
    }

    override fun stringConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, String>? {
        if (twitchIdTypes.any { it == type }) {
            return twitchIdConverter
        }
        return null
    }
}
