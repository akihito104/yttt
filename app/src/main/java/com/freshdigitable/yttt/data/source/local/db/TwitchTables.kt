package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import java.time.Instant

@Entity(tableName = "twitch_user")
data class TwitchUserTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: TwitchUser.Id,
    @ColumnInfo(name = "login_name")
    override val loginName: String,
    @ColumnInfo(name = "display_name")
    override val displayName: String,
) : TwitchUser

@Entity(
    tableName = "twitch_user_detail",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            childColumns = ["user_id"],
            parentColumns = ["id"],
        ),
    ],
)
class TwitchUserDetailTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "user_id")
    val id: TwitchUser.Id,
    @ColumnInfo(name = "profile_image_url")
    val profileImageUrl: String,
    @ColumnInfo(name = "views_count")
    val viewsCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo("description")
    val description: String,
)

data class TwitchUserDetailDbView(
    @Embedded
    private val user: TwitchUserTable,
    @ColumnInfo("description")
    override val description: String,
    @ColumnInfo("profile_image_url")
    override val profileImageUrl: String,
    @ColumnInfo("views_count")
    override val viewsCount: Int,
    @ColumnInfo("created_at")
    override val createdAt: Instant,
) : TwitchUserDetail, TwitchUser by user {
    companion object {
        internal const val SQL_USER_DETAIL = "SELECT u.*, d.profile_image_url, d.views_count, " +
            "d.created_at, d.description FROM twitch_user_detail AS d " +
            "INNER JOIN twitch_user AS u ON d.user_id = u.id"
        internal const val SQL_EMBED_PREFIX = "u_"
        internal const val SQL_EMBED_ALIAS = "u.id AS ${SQL_EMBED_PREFIX}id, " +
            "u.display_name AS ${SQL_EMBED_PREFIX}display_name, u.login_name AS ${SQL_EMBED_PREFIX}login_name, " +
            "u.description AS ${SQL_EMBED_PREFIX}description, u.created_at AS ${SQL_EMBED_PREFIX}created_at, " +
            "u.views_count AS ${SQL_EMBED_PREFIX}views_count, u.profile_image_url AS ${SQL_EMBED_PREFIX}profile_image_url"
    }
}

@Entity(
    tableName = "twitch_user_detail_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserDetailTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchUserDetailExpireTable(
    @PrimaryKey
    @ColumnInfo("user_id", index = true)
    val userId: TwitchUser.Id,
    @ColumnInfo("expired_at", defaultValue = "null")
    val expiredAt: Instant? = null,
)

@Entity(
    tableName = "twitch_broadcaster",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["follower_user_id"],
        ),
    ],
    primaryKeys = ["user_id", "follower_user_id"]
)
class TwitchBroadcasterTable(
    @ColumnInfo(name = "user_id")
    val id: TwitchUser.Id,
    @ColumnInfo(name = "follower_user_id", index = true)
    val followerId: TwitchUser.Id,
    @ColumnInfo(name = "followed_at")
    val followedAt: Instant,
)

@Entity(
    tableName = "twitch_broadcaster_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["follower_user_id"],
        ),
    ],
)
class TwitchBroadcasterExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("follower_user_id", index = true)
    val followerId: TwitchUser.Id,
    @ColumnInfo("expire_at")
    val expireAt: Instant,
)

data class TwitchBroadcasterDb(
    @Embedded
    private val user: TwitchUserTable,
    @ColumnInfo("followed_at")
    override val followedAt: Instant
) : TwitchBroadcaster, TwitchUser by user

@Entity(
    tableName = "twitch_auth_user",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchAuthorizedUserTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("user_id")
    val userId: TwitchUser.Id,
)

@Entity(
    tableName = "twitch_stream",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchStreamTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: TwitchStream.Id,
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "thumbnail_url_base")
    val thumbnailUrlBase: String,
    @ColumnInfo(name = "view_count")
    val viewCount: Int,
    @ColumnInfo(name = "language")
    val language: String,
    @ColumnInfo(name = "game_id")
    val gameId: String,
    @ColumnInfo(name = "game_name")
    val gameName: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
    @ColumnInfo(name = "tags")
    val tags: List<String>,
    @ColumnInfo(name = "is_mature")
    val isMature: Boolean,
)

@DatabaseView(
    viewName = "twitch_stream_view",
    value = "SELECT s.*, ${TwitchUserDetailDbView.SQL_EMBED_ALIAS} FROM twitch_stream AS s " +
        "INNER JOIN (${TwitchUserDetailDbView.SQL_USER_DETAIL}) AS u ON u.id = s.user_id",
)
data class TwitchStreamDbView(
    @Embedded
    private val streamEntity: TwitchStreamTable,
    @Embedded(TwitchUserDetailDbView.SQL_EMBED_PREFIX)
    override val user: TwitchUserDetailDbView,
) : TwitchStream {
    override val gameId: String get() = streamEntity.gameId
    override val gameName: String get() = streamEntity.gameName
    override val type: String get() = streamEntity.type
    override val startedAt: Instant get() = streamEntity.startedAt
    override val tags: List<String> get() = streamEntity.tags
    override val isMature: Boolean get() = streamEntity.isMature
    override val id: TwitchStream.Id get() = streamEntity.id
    override val title: String get() = streamEntity.title
    override val thumbnailUrlBase: String get() = streamEntity.thumbnailUrlBase
    override val viewCount: Int get() = streamEntity.viewCount
    override val language: String get() = streamEntity.language
}

@Entity(
    tableName = "twitch_stream_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchStreamExpireTable(
    @PrimaryKey
    @ColumnInfo("user_id", index = true)
    val userId: TwitchUser.Id,
    @ColumnInfo("expired_at")
    val expiredAt: Instant,
)

@Entity(
    tableName = "twitch_channel_schedule_vacation",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchChannelVacationScheduleTable(
    @PrimaryKey
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
    @Embedded
    val vacation: TwitchChannelVacationSchedule?,
)

class TwitchChannelVacationSchedule(
    @ColumnInfo(name = "vacation_start")
    override val startTime: Instant,
    @ColumnInfo(name = "vacation_end")
    override val endTime: Instant,
) : TwitchChannelSchedule.Vacation

@Entity(
    tableName = "twitch_channel_schedule_stream",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchStreamScheduleTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: TwitchChannelSchedule.Stream.Id,
    @ColumnInfo(name = "start_time")
    override val startTime: Instant,
    @ColumnInfo(name = "end_time")
    override val endTime: Instant,
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "canceled_until")
    override val canceledUntil: String?,
    @Embedded
    override val category: TwitchStreamCategory?,
    @ColumnInfo(name = "is_recurring")
    override val isRecurring: Boolean,
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
) : TwitchChannelSchedule.Stream

class TwitchStreamCategory(
    @ColumnInfo(name = "category_id")
    override val id: String,
    @ColumnInfo(name = "category_name")
    override val name: String,
) : TwitchChannelSchedule.StreamCategory

class TwitchChannelScheduleDb(
    @Relation(
        parentColumn = "${TwitchUserDetailDbView.SQL_EMBED_PREFIX}id",
        entityColumn = "user_id"
    )
    override val segments: List<TwitchStreamScheduleTable>?,
    @Embedded(TwitchUserDetailDbView.SQL_EMBED_PREFIX)
    override val broadcaster: TwitchUserDetailDbView,
    @Embedded
    override val vacation: TwitchChannelVacationSchedule?,
) : TwitchChannelSchedule

@Entity(
    tableName = "twitch_channel_schedule_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchChannelVacationScheduleTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchChannelScheduleExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("user_id")
    val userId: TwitchUser.Id,
    @ColumnInfo("expired_at")
    val expiredAt: Instant,
)
