package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.BuildConfig
import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveSubscriptionEntity
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
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
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(OkHttpClient.Builder().addInterceptor(tokenInterceptor).build())
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
        response.raw().request().url().toString()
    }

    private val helix: TwitchHelixService by lazy {
        retrofit.create(TwitchHelixService::class.java)
    }

    suspend fun findUsersById(
        ids: Collection<LiveChannel.Id>? = null,
    ): List<LiveChannelDetail> = withContext(Dispatchers.IO) {
        val response = helix.getUser(id = ids?.map { it.value }).execute()
        val users = response.body() ?: return@withContext emptyList()
        users.data.map { TwitchLiveChannel(it) }
    }

    suspend fun fetchMe(): LiveChannelDetail? = withContext(Dispatchers.IO) {
        findUsersById().firstOrNull()
    }

    private val followings = mutableListOf<LiveSubscription>()
    suspend fun fetchAllFollowings(
        userId: LiveChannel.Id,
    ): List<LiveSubscription> = withContext(Dispatchers.IO) {
        if (followings.isNotEmpty()) {
            return@withContext followings
        }
        var cursor: String?
        val items = mutableListOf<LiveSubscription>()
        do {
            val response = helix.getFollowing(userId = userId.value, itemsPerPage = 100).execute()
            val body = response.body() ?: break
            if (body.total <= 0) {
                break
            }
            val userIds = body.data.map { LiveChannel.Id(it.id) }
            val users = findUsersById(userIds)
            val itemCount = items.size
            val subs = body.data.mapIndexed { i, b ->
                val u = users.first { it.id.value == b.id }
                LiveSubscriptionEntity(
                    id = LiveSubscription.Id(b.id),
                    subscribeSince = b.followedAt,
                    channel = u,
                    order = itemCount + i,
                )
            }
            items.addAll(subs)
            cursor = body.pagination.cursor
        } while (cursor != null)
        followings.clear()
        followings.addAll(items)
        items
    }

    suspend fun fetchFollowings(
        userId: LiveChannel.Id,
        itemsPerPage: Int? = null
    ): List<LiveChannel> = withContext(Dispatchers.IO) {
        val response = helix.getFollowing(userId = userId.value, itemsPerPage = itemsPerPage)
            .execute()
        response.body()?.data?.map {
            LiveChannelEntity(
                id = LiveChannel.Id(it.id),
                title = it.displayName,
                iconUrl = ""
            )
        } ?: emptyList()
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
    override val id: LiveChannel.Id = LiveChannel.Id(user.id)
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
    val data: List<Broadcaster>,
    @SerializedName("pagination")
    val pagination: Pagination,
    @SerializedName("total")
    val total: Int,
) {
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

    class Pagination(@SerializedName("cursor") val cursor: String?)
}
