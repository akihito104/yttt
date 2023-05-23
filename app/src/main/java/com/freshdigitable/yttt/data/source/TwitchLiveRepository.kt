package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.BuildConfig
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.model.LiveVideoEntity
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRepository @Inject constructor(
    tokenInterceptor: TwitchTokenInterceptor,
) {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : TypeAdapter<Instant?>() {
            override fun write(out: JsonWriter?, value: Instant?) {
                out?.value(value?.toString())
            }

            override fun read(`in`: JsonReader?): Instant? {
                val str = `in`?.nextString() ?: return null
                return Instant.parse(str)
            }
        })
        .create()
    private val okhttp = OkHttpClient.Builder()
        .addInterceptor(tokenInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okhttp)
        .build()

    suspend fun getAuthorizeUrl(): String = withContext(Dispatchers.IO) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://id.twitch.tv/")
            .build()
        val response = retrofit.create(TwitchOauth::class.java).authorizeImplicitly(
            clientId = BuildConfig.TWITCH_CLIENT_ID,
            redirectUri = BuildConfig.TWITCH_REDIRECT_URI,
            scope = "user:read:follows",
        ).execute()
        response.raw().request.url.toString()
    }

    private val helix: TwitchHelixService by lazy {
        retrofit.create(TwitchHelixService::class.java)
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

    private val users = mutableMapOf<LiveChannel.Id, LiveChannelDetail>()
    private val me: LiveChannelDetail? = null
    suspend fun findUsersById(
        ids: Collection<LiveChannel.Id>? = null,
    ): List<LiveChannelDetail> {
        if (ids == null && me != null) {
            return listOf(me)
        }
        val cache = ids?.mapNotNull { users[it] } ?: emptyList()
        if (ids != null && cache.size == ids.size) {
            return cache
        }
        val remoteIds = if (ids == null) null else ids - cache.map { it.id }.toSet()
        return fetch {
            val response = getUser(id = remoteIds?.map { it.value }).execute()
            val users = response.body() ?: return@fetch emptyList()
            users.data.map { TwitchLiveChannel(it) } + cache
        }
    }

    suspend fun fetchMe(): LiveChannelDetail? = withContext(Dispatchers.IO) {
        findUsersById().firstOrNull()
    }

    private val followings = mutableListOf<LiveSubscription>()
    suspend fun fetchAllFollowings(
        userId: LiveChannel.Id,
    ): List<LiveSubscription> {
        if (followings.isNotEmpty()) {
            return followings
        }
        val items =
            fetchAll { getFollowing(userId = userId.value, itemsPerPage = 100, cursor = it) }
        val userIds = items.map { LiveChannel.Id(it.id, LivePlatform.TWITCH) }
        val users = findUsersById(userIds)
        return items.mapIndexed { i, b ->
            LiveSubscriptionEntity(
                id = LiveSubscription.Id(b.id, LivePlatform.TWITCH),
                subscribeSince = b.followedAt,
                channel = users.firstOrNull { it.id.value == b.id } ?: LiveChannelEntity(
                    id = LiveChannel.Id(b.id, LivePlatform.TWITCH),
                    title = b.displayName,
                    iconUrl = "",
                ),
                order = i,
            )
        }
    }

    private val _onAir = MutableStateFlow<List<LiveVideo>>(emptyList())
    val onAir: Flow<List<LiveVideo>> = _onAir
    suspend fun fetchFollowedStreams(): List<LiveVideo> {
        val me = fetchMe() ?: return emptyList()
        val items = fetchAll { getFollowedStreams(me.id.value, cursor = it) }
        val userIds = items.map { LiveChannel.Id(it.userId, LivePlatform.TWITCH) }
        val users = findUsersById(userIds)
        val res = items.map { v ->
            object : LiveVideoDetail, LiveVideo by LiveVideoEntity(
                id = LiveVideo.Id(v.id, LivePlatform.TWITCH),
                title = v.title,
                channel = users.firstOrNull { it.id.value == v.userId } ?: LiveChannelEntity(
                    id = LiveChannel.Id(v.userId, LivePlatform.TWITCH),
                    title = v.displayName,
                    iconUrl = "",
                ),
                actualStartDateTime = v.startedAt,
                thumbnailUrl = v.thumbnailUrl,
                scheduledStartDateTime = v.startedAt, // XXX
            ) {
                override val description: String
                    get() = ""
                override val viewerCount: BigInteger?
                    get() = BigInteger.valueOf(v.viewerCount.toLong())

            }
        }
        _onAir.value = res
        return res
    }

    private val _upcoming = MutableStateFlow<Map<LiveChannel.Id, List<LiveVideo>>>(emptyMap())
    val upcoming: Flow<List<LiveVideo>> = _upcoming.map { it.values.flatten() }
    suspend fun fetchFollowedStreamSchedule(
        id: LiveChannel.Id,
        maxCount: Int = 10,
    ): List<LiveVideo> {
        val s =
            fetchAll(maxCount) { getChannelStreamSchedule(broadcasterId = id.value, cursor = it) }
        val items = s.flatMap { it.segments?.toList() ?: emptyList() }
        val user = findUsersById(listOf(id)).firstOrNull() ?: LiveChannelEntity(
            id = id,
            title = s.first().broadcasterName,
            iconUrl = ""
        )
        val res = items.map { v ->
            LiveVideoEntity(
                id = LiveVideo.Id(v.id, LivePlatform.TWITCH),
                title = v.title,
                scheduledStartDateTime = v.startTime,
                scheduledEndDateTime = v.endTime,
                thumbnailUrl = "",
                channel = user,
            )
        }
        _upcoming.update {
            it.toMutableMap().apply { this[id] = res }
        }
        return res
    }

    fun fetchStreamDetail(id: LiveVideo.Id): LiveVideo {
        check(id.platform == LivePlatform.TWITCH)
        return _onAir.value.firstOrNull { it.id == id } ?: _upcoming.value.values.flatten()
            .first { it.id == id }
    }

    suspend fun fetchVideosByChannelId(id: LiveChannel.Id, itemCount: Int = 20): List<LiveVideo> {
        val resp = fetch { getVideoByUserId(userId = id.value, itemsPerPage = itemCount).execute() }
        val videos = resp.body()?.data ?: emptyArray()
        return videos.map {
            LiveVideoEntity(
                id = LiveVideo.Id(it.id),
                thumbnailUrl = it.thumbnailUrl.replace("%{width}", "1920")
                    .replace("%{height}", "1080"),
                channel = LiveChannelEntity(
                    id = LiveChannel.Id(it.userId),
                    title = it.userDisplayName,
                    iconUrl = "",
                ),
                title = it.title,
                scheduledStartDateTime = null,
                actualStartDateTime = it.publishedAt,
            )
        }
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchLiveRepository::class.simpleName
    }
}

interface TwitchOauth {
    @GET("oauth2/authorize?response_type=token")
    fun authorizeImplicitly(
        @Query("client_id") clientId: String,
        @Query("force_verify") forceVerify: Boolean? = null,
        @Query("redirect_uri") redirectUri: String,
        @Query("scope") scope: String,
        @Query("state") state: String = UUID.randomUUID().toString(),
    ): Call<ResponseBody>
}

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object
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
    ): Call<VideosResponse>
}

class TwitchUserResponse(@SerializedName("data") val data: List<TwitchUser>)
class TwitchUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("display_name")
    val displayName: String,
    @SerializedName("profile_image_url")
    val profileImageUrl: String,
    @SerializedName("view_count")
    val viewsCount: Int,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("login")
    val loginName: String,
    @SerializedName("description")
    val description: String,
)

private data class TwitchLiveChannel(private val user: TwitchUser) : LiveChannelDetail {
    override val id: LiveChannel.Id = LiveChannel.Id(user.id, LivePlatform.TWITCH)
    override val title: String
        get() = user.displayName
    override val iconUrl: String
        get() = user.profileImageUrl
    override val bannerUrl: String?
        get() = null
    override val subscriberCount: BigInteger
        get() = BigInteger.ZERO
    override val isSubscriberHidden: Boolean
        get() = false
    override val videoCount: BigInteger
        get() = BigInteger.ZERO
    override val viewsCount: BigInteger = BigInteger.valueOf(user.viewsCount.toLong())
    override val publishedAt: Instant = Instant.parse(user.createdAt)
    override val customUrl: String
        get() = user.loginName
    override val keywords: Collection<String>
        get() = emptyList()
    override val description: String
        get() = user.description
    override val uploadedPlayList: LivePlaylist.Id?
        get() = null
}

@Singleton
class TwitchTokenInterceptor @Inject constructor(
    private val accountRepository: AccountRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accountRepository.getTwitchToken() ?: return chain.proceed(chain.request())
        val req = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Client-Id", BuildConfig.TWITCH_CLIENT_ID)
            .build()
        return chain.proceed(req)
    }
}

class FollowedChannelsResponse(
    @SerializedName("data")
    val data: Array<Broadcaster>,
    @SerializedName("pagination")
    override val pagination: Pagination,
    @SerializedName("total")
    val total: Int,
) : Pageable<FollowedChannelsResponse.Broadcaster> {
    override fun getItems(): Array<Broadcaster> = data
    class Broadcaster(
        @SerializedName("broadcaster_id")
        val id: String,
        @SerializedName("broadcaster_login")
        val loginName: String,
        @SerializedName("broadcaster_name")
        val displayName: String,
        @SerializedName("followed_at")
        val followedAt: Instant,
    )
}

class Pagination(@SerializedName("cursor") val cursor: String?)
interface Pageable<T> {
    val pagination: Pagination
    fun getItems(): Array<T>
}

class FollowingStreamsResponse(
    @SerializedName("data")
    val data: Array<FollowingStream>,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<FollowingStreamsResponse.FollowingStream> {
    override fun getItems(): Array<FollowingStream> = data
    class FollowingStream(
        @SerializedName("id")
        val id: String,
        @SerializedName("user_id")
        val userId: String,
        @SerializedName("user_login")
        val loginName: String,
        @SerializedName("user_name")
        val displayName: String,
        @SerializedName("game_id")
        val gameId: String,
        @SerializedName("game_name")
        val gameName: String,
        @SerializedName("type")
        val type: String,
        @SerializedName("title")
        val title: String,
        @SerializedName("viewer_count")
        val viewerCount: Int,
        @SerializedName("started_at")
        val startedAt: Instant,
        @SerializedName("language")
        val language: String,
        @SerializedName("thumbnail_url")
        val _thumbnailUrl: String,
        @SerializedName("tags")
        val tags: Array<String>,
        @SerializedName("is_mature")
        val isMature: Boolean,
    ) {
        val thumbnailUrl: String
            get() = _thumbnailUrl.replace("{width}x{height}", "1920x1080")
    }
}

class ChannelStreamScheduleResponse(
    @SerializedName("data")
    val data: ChannelStreamSchedule,
    @SerializedName("pagination")
    override val pagination: Pagination,
) : Pageable<ChannelStreamScheduleResponse.ChannelStreamSchedule> {
    override fun getItems(): Array<ChannelStreamSchedule> = arrayOf(data)
    class ChannelStreamSchedule(
        @SerializedName("segments")
        val segments: Array<StreamSchedule>?,
        @SerializedName("broadcaster_id")
        val broadcasterId: String,
        @SerializedName("broadcaster_name")
        val broadcasterName: String,
        @SerializedName("broadcaster_login")
        val broadcasterLogin: String,
        @SerializedName("vacation")
        val vacation: Vacation?,
    )

    class StreamSchedule(
        @SerializedName("id")
        val id: String,
        @SerializedName("start_time")
        val startTime: Instant,
        @SerializedName("end_time")
        val endTime: Instant,
        @SerializedName("title")
        val title: String,
        @SerializedName("canceled_until")
        val canceledUntil: String?,
        @SerializedName("category")
        val category: StreamCategory?,
        @SerializedName("is_recurring")
        val isRecurring: Boolean,
    )

    class StreamCategory(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
    )

    class Vacation(
        @SerializedName("start_time")
        val startTime: Instant,
        @SerializedName("end_time")
        val endTime: Instant,
    )
}

class VideosResponse(
    @SerializedName("data")
    val data: Array<Video>,
    @SerializedName("pagination")
    val pagination: Pagination,
) {
    class Video(
        @SerializedName("id")
        val id: String,
        @SerializedName("stream_id")
        val streamId: String,
        @SerializedName("user_id")
        val userId: String,
        @SerializedName("user_login")
        val userLoginName: String,
        @SerializedName("user_name")
        val userDisplayName: String,
        @SerializedName("title")
        val title: String,
        @SerializedName("description")
        val description: String,
        @SerializedName("created_at")
        val createdAt: Instant,
        @SerializedName("published_at")
        val publishedAt: Instant,
        @SerializedName("url")
        val url: String,
        @SerializedName("thumbnail_url")
        val thumbnailUrl: String,
        @SerializedName("viewable")
        val viewable: String,
        @SerializedName("view_count")
        val viewCount: Int,
        @SerializedName("language")
        val language: String,
        @SerializedName("type")
        val type: String,
        @SerializedName("duration")
        val duration: String,
        @SerializedName("muted_segments")
        val mutedSegments: Array<MutedSegment>,
    )

    class MutedSegment(
        @SerializedName("duration")
        val duration: Int, // [sec.]
        @SerializedName("offset")
        val offset: Int, // [sec.]
    )
}
