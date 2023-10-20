package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.BuildConfig
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.TwitchVideoDetail
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRemoteDataSource @Inject constructor(
    private val oauth: TwitchOauth,
    private val helix: TwitchHelixService,
) : TwitchLiveDataSource {
    override suspend fun getAuthorizeUrl(): String = withContext(Dispatchers.IO) {
        val response = oauth.authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
        ).execute()
        response.raw().request.url.toString()
    }

    private suspend fun <T> fetch(task: suspend TwitchHelixService.() -> T): T =
        withContext(Dispatchers.IO) { helix.task() }

    private suspend fun <E, P : Pageable<E>> fetchAll(
        maxCount: Int? = null,
        call: TwitchHelixService.(String?) -> Call<P>,
    ): List<E> = fetch {
        var cursor: String? = null
        val items = mutableListOf<E>()
        do {
            val response = helix.call(cursor).execute()
            val body = response.body() ?: break
            items.addAll(body.getItems())
            cursor = body.pagination.cursor

        } while (cursor != null && (maxCount == null || maxCount < items.size))
        items
    }

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> =
        fetch {
            val response = getUser(id = ids?.map { it.value }).execute()
            response.body()?.data ?: return@fetch emptyList()
        }

    override suspend fun fetchMe(): TwitchUser? = findUsersById().firstOrNull()

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        return fetchAll { getFollowing(userId = userId.value, itemsPerPage = 100, cursor = it) }
    }

    override suspend fun fetchFollowedStreams(): List<TwitchStream> {
        val me = fetchMe() ?: return emptyList()
        return fetchFollowedStreams(me.id)
    }

    suspend fun fetchFollowedStreams(id: TwitchUser.Id): List<TwitchStream> =
        fetchAll { getFollowedStreams(id.value, cursor = it) }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int,
    ): List<TwitchChannelSchedule> = fetchAll(maxCount) {
        getChannelStreamSchedule(broadcasterId = id.value, cursor = it)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int
    ): List<TwitchVideoDetail> {
        val resp = fetch { getVideoByUserId(userId = id.value, itemsPerPage = itemCount).execute() }
        return resp.body()?.data?.toList() ?: emptyList()
    }

    override val onAir: Flow<List<TwitchStream>> get() = throw AssertionError()
    override val upcoming: Flow<List<TwitchChannelSchedule>> get() = throw AssertionError()
    override suspend fun fetchStreamDetail(id: TwitchVideo.TwitchVideoId): TwitchVideo<TwitchVideo.TwitchVideoId> =
        throw AssertionError()
}

interface TwitchOauth {
    /// https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/
    @GET("oauth2/authorize?response_type=token")
    fun authorizeImplicitly(
        @Query("client_id") clientId: String,
        @Query("force_verify") forceVerify: Boolean? = null,
        @Query("redirect_uri") redirectUri: String,
        @Query("scope") scope: String,
        @Query("state") state: String = UUID.randomUUID().toString(),
    ): Call<ResponseBody>
}

interface TwitchHelixService {
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
        @Query("end_time") endTime: Instant? = null,
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

class TwitchUserResponse(@SerializedName("data") val data: List<TwitchUserDetailRemote>)
class TwitchUserDetailRemote(
    @SerializedName("id")
    override val id: TwitchUser.Id,
    @SerializedName("display_name")
    override val displayName: String,
    @SerializedName("profile_image_url")
    override val profileImageUrl: String,
    @SerializedName("view_count")
    override val viewsCount: Int,
    @SerializedName("created_at")
    override val createdAt: Instant,
    @SerializedName("login")
    override val loginName: String,
    @SerializedName("description")
    override val description: String,
) : TwitchUserDetail

class TwitchUserRemote(
    override val id: TwitchUser.Id,
    override val loginName: String,
    override val displayName: String
) : TwitchUser

@Singleton
class TwitchTokenInterceptor @Inject constructor(
    private val accountDataSource: AccountLocalDataSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.twitch.tv") {
            return chain.proceed(request)
        }
        val token = accountDataSource.getTwitchToken() ?: return chain.proceed(request)
        val req = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Client-Id", BuildConfig.TWITCH_CLIENT_ID)
            .build()
        return chain.proceed(req)
    }
}

class Broadcaster(
    @SerializedName("broadcaster_id")
    override val id: TwitchUser.Id,
    @SerializedName("broadcaster_login")
    override val loginName: String,
    @SerializedName("broadcaster_name")
    override val displayName: String,
    @SerializedName("followed_at")
    override val followedAt: Instant,
) : TwitchBroadcaster

class FollowedChannelsResponse(
    @SerializedName("data")
    val data: Array<Broadcaster>,
    @SerializedName("pagination")
    override val pagination: Pagination,
    @SerializedName("total")
    val total: Int,
) : Pageable<Broadcaster> {
    override fun getItems(): Array<Broadcaster> = data
}

class Pagination(@SerializedName("cursor") val cursor: String?)
interface Pageable<T> {
    val pagination: Pagination
    fun getItems(): Array<T>
}

data class FollowingStream(
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
            TwitchUser.Id(userId),
            loginName = loginName,
            displayName = displayName
        )
    override val url: String
        get() = "https://twitch.tv/${user.loginName}"
}

class FollowingStreamsResponse(
    @SerializedName("data")
    val data: Array<FollowingStream>,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<FollowingStream> {
    override fun getItems(): Array<FollowingStream> = data
}

class ChannelStreamSchedule(
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
        override val endTime: Instant,
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

class ChannelStreamScheduleResponse(
    @SerializedName("data")
    val data: ChannelStreamSchedule,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<ChannelStreamSchedule> {
    override fun getItems(): Array<ChannelStreamSchedule> = arrayOf(data)
}

class TwitchVideoRemote(
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

class TwitchVideosResponse(
    @SerializedName("data")
    val data: Array<TwitchVideoRemote>,
    @SerializedName("pagination")
    val pagination: Pagination,
)
