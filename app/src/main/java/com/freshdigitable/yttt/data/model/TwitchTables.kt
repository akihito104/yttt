package com.freshdigitable.yttt.data.model

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.local.db.Converter
import com.freshdigitable.yttt.data.source.local.db.IdConverter
import java.time.Instant

@Entity(tableName = "twitch_user")
class TwitchUserTable(
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
)

@Entity(
    tableName = "twitch_broadcaster",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
class TwitchBroadcasterTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "user_id")
    val id: TwitchUser.Id,
    @ColumnInfo(name = "followed_at")
    val followedAt: Instant,
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
    value = "SELECT *, u.id AS user_id, u.display_name AS user_display_name, u.login_name AS user_login_name" +
        " FROM twitch_stream AS s INNER JOIN twitch_user AS u ON u.id = s.user_id",
)
class TwitchStreamDbView(
    @ColumnInfo(name = "id")
    override val id: TwitchStream.Id,
    @Embedded("user_")
    override val user: TwitchUserTable,
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "thumbnail_url_base")
    override val thumbnailUrlBase: String,
    @ColumnInfo(name = "view_count")
    override val viewCount: Int,
    @ColumnInfo(name = "language")
    override val language: String,
    @ColumnInfo(name = "game_id")
    override val gameId: String,
    @ColumnInfo(name = "game_name")
    override val gameName: String,
    @ColumnInfo(name = "type")
    override val type: String,
    @ColumnInfo(name = "started_at")
    override val startedAt: Instant,
    @ColumnInfo(name = "tags")
    override val tags: List<String>,
    @ColumnInfo(name = "is_mature")
    override val isMature: Boolean,
) : TwitchStream

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
    val vacation: TwitchChannelVacationSchedule,
)

class TwitchChannelVacationSchedule(
    @ColumnInfo(name = "vacation_start")
    override val startTime: Instant,
    @ColumnInfo(name = "vacation_end")
    override val endTime: Instant,
) : TwitchChannelSchedule.Vacation

@Entity(tableName = "twitch_channel_schedule_stream")
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
) : TwitchChannelSchedule.Stream

class TwitchStreamCategory(
    @ColumnInfo(name = "category_id")
    override val id: String,
    @ColumnInfo(name = "category_name")
    override val name: String,
) : TwitchChannelSchedule.StreamCategory

class TwitchChannelScheduleDb(
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    override val segments: List<TwitchStreamScheduleTable>?,
    @Embedded("user_")
    override val broadcaster: TwitchUserTable,
    @Embedded
    override val vacation: TwitchChannelSchedule.Vacation?,
) : TwitchChannelSchedule

class TwitchUserIdConverter : IdConverter<TwitchUser.Id>(createObject = { TwitchUser.Id(it) })
class TwitchStreamScheduleIdConverter :
    IdConverter<TwitchChannelSchedule.Stream.Id>(createObject = { TwitchChannelSchedule.Stream.Id(it) })

class TwitchStreamIdConverter : IdConverter<TwitchStream.Id>(createObject = { TwitchStream.Id(it) })
class CsvConverter : Converter<String, List<String>>(
    serialize = { it.joinToString() },
    createObject = { it.split(",") }
)
