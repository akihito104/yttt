package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant

internal class TwitchUserDetailRemote(
    @SerializedName("id")
    override val id: TwitchUser.Id,
    @SerializedName("display_name")
    override val displayName: String,
    @SerializedName("profile_image_url")
    override val profileImageUrl: String,
    @SerializedName("created_at")
    override val createdAt: Instant,
    @SerializedName("login")
    override val loginName: String,
    @SerializedName("description")
    override val description: String,
) : TwitchUserDetail {
    override val cacheControl: CacheControl
        get() = CacheControl.create(null, Duration.ofMinutes(5))
}

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
    override val gameId: TwitchCategory.Id,
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
        override val id: TwitchCategory.Id,
        @SerializedName("name")
        override val name: String,
    ) : TwitchCategory

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

internal class TwitchGameRemote(
    @SerializedName("id") override val id: TwitchCategory.Id,
    @SerializedName("name") override val name: String,
    @SerializedName("box_art_url") override val artUrlBase: String?,
    @SerializedName("igdb_id") override val igdbId: String?,
) : TwitchCategory

// gson just only deserializes in this project
internal fun createGson(): Gson = GsonBuilder()
    .registerJsonDeserializer { Instant.parse(it.asString) }
    .registerTypeHierarchyDeserializer<TwitchId>(
        mapOf(
            deserializerWithType { TwitchUser.Id(it.asString) },
            deserializerWithType { TwitchStream.Id(it.asString) },
            deserializerWithType { TwitchCategory.Id(it.asString) },
            deserializerWithType { TwitchChannelSchedule.Stream.Id(it.asString) },
            deserializerWithType { TwitchVideo.Id(it.asString) },
        )
    )
    .create()

typealias Deserializer<T> = (JsonElement) -> T

private inline fun <reified T> deserializerWithType(noinline deserialize: Deserializer<T>): Pair<Class<T>, (JsonElement) -> T> =
    T::class.java to deserialize

private inline fun <reified T> GsonBuilder.registerTypeHierarchyDeserializer(
    table: Map<Type, Deserializer<T>>,
): GsonBuilder = registerTypeHierarchyAdapter(T::class.java, object : JsonDeserializer<T> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): T = checkNotNull(table[typeOfT]?.invoke(json)) { "unsupported type: $typeOfT" }
})

private inline fun <reified O> GsonBuilder.registerJsonDeserializer(
    crossinline deserialize: Deserializer<O>,
): GsonBuilder = registerTypeAdapter(O::class.java, object : JsonDeserializer<O> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): O = deserialize(json)
})
