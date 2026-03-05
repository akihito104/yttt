package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import java.time.Instant

class TwitchUserJson(
    override val id: TwitchUser.Id,
    override val loginName: String = "user$id",
    override val displayName: String = loginName,
) : TwitchUser, Json {
    override fun toString(): String = Json.obj {
        this["id"] = id.value
        this["login"] = loginName
        this["display_name"] = displayName
        this["type"] = ""
        this["broadcaster_type"] = "partner"
        this["description"] =
            "Supporting third-party developers building Twitch integrations from chatbots to game integrations."
        this["profile_image_url"] =
            "https://static-cdn.jtvnw.net/jtv_user_pictures/8a6381c7-d0c0-4576-b179-38bd5ce1d6af-profile_image-300x300.png"
        this["offline_image_url"] =
            "https://static-cdn.jtvnw.net/jtv_user_pictures/3f13ab61-ec78-4fe6-8481-8682cb3b0ac2-channel_offline_image-1920x1080.png"
        this["view_count"] = 5980557
        this["email"] = "not-real@email.com"
        this["created_at"] = "2016-12-14T20:32:28Z"
    }.toString()
}

class TwitchFollowingJson(private val user: TwitchUser) : Json, TwitchUser by user {
    override fun toString(): String = Json.obj {
        this["broadcaster_id"] = user.id.value
        this["broadcaster_login"] = user.loginName
        this["broadcaster_name"] = user.displayName
        this["followed_at"] = "2022-05-24T22:22:08Z"
    }.toString()
}

class TwitchFollowedStreamJson(
    private val id: String,
    private val user: TwitchUserJson,
    private val game: TwitchCategory,
) : Json {
    override fun toString(): String = Json.obj {
        this["id"] = id
        this["user_id"] = user.id.value
        this["user_login"] = user.loginName
        this["user_name"] = user.displayName
        this["game_id"] = game.id.value
        this["game_name"] = game.name
        this["type"] = "live"
        this["title"] = "AWS Howdy Partner! Y'all welcome ExtraHop to the show!"
        this["viewer_count"] = 20
        this["started_at"] = "2021-03-31T20:57:26Z"
        this["language"] = "en"
        this["thumbnail_url"] = "https://<url-is-here>/live_user_aws-{width}x{height}.jpg"
        this["tag_ids"] = emptyList<String>()
        this["tags"] = listOf("English")
    }.toString()
}

class TwitchScheduleJson(
    private val id: String,
    private val game: TwitchGameJson,
    val startTime: Instant,
) : Json {
    override fun toString(): String = Json.obj {
        this["id"] = id
        this["start_time"] = startTime.toString()
        this["end_time"] = "2021-07-01T19:00:00Z"
        this["title"] = "TwitchDev Monthly Update // July 1, 2021"
        this["canceled_until"] = null
        this["category"] = game
        this["is_recurring"] = false
    }.toString()
}

class TwitchChannelScheduleJson(
    private val schedules: List<TwitchScheduleJson>,
    private val broadcaster: TwitchUser,
    private val vacation: TwitchChannelSchedule.Vacation? = null,
) : ResponseJson {
    override fun toString(): String = Json.obj {
        this["data"] = Json.obj {
            this["segments"] = schedules
            this["broadcaster_id"] = broadcaster.id.value
            this["broadcaster_name"] = broadcaster.displayName
            this["broadcaster_login"] = broadcaster.loginName
            this["vacation"] = vacation
        }
        this["pagenation"] = Json.obj {
            this["cursor"] = null
        }
    }.toString()
}

class TwitchGameJson(
    override val id: TwitchCategory.Id,
    override val name: String,
) : TwitchCategory, Json {
    override fun toString(): String = Json.obj {
        this["id"] = id.value
        this["name"] = name
        this["box_art_url"] = artUrlBase
        this["igdb_id"] = igdbId
    }.toString()
}

inline fun <reified T : Json> List<T>.twitchResponse(
    total: Int? = null,
    cursor: String? = null,
): ResponseJson = when (T::class) {
    TwitchUserJson::class -> TwitchResponseJson(this)
    TwitchFollowingJson::class -> TwitchPaginationResponseJson(this, total ?: size, cursor)
    TwitchFollowedStreamJson::class -> TwitchPaginationResponseJson(this, total ?: size, cursor)
    TwitchGameJson::class -> TwitchResponseJson(this)
    TwitchChannelScheduleJson::class -> TwitchPaginationResponseJson(this, null, cursor)
    else -> throw AssertionError("Unknown type: ${T::class}")
}

class TwitchResponseJson(
    private val data: List<Json>,
) : ResponseJson {
    override fun toString(): String = Json.obj {
        this["data"] = data
    }.toString()
}

class TwitchPaginationResponseJson(
    private val data: List<Json>,
    private val total: Int? = data.size,
    private val cursor: String? = null,
) : ResponseJson {
    override fun toString(): String = Json.obj {
        this["data"] = data
        this["total"] = total
        this["pagination"] = Json.obj {
            this["cursor"] = cursor
        }
    }.toString()
}

class TwitchErrorJson private constructor(
    override val statusCode: Int,
    private val message: String,
) : ResponseJson {
    companion object {
        fun badRequest() = TwitchErrorJson(400, "bad request")
        fun notFound() = TwitchErrorJson(404, "not found")
        fun internalError() = TwitchErrorJson(500, "internal error")
    }

    override fun toString(): String = message
}
