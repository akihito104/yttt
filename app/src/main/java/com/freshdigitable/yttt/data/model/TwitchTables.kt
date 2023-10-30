package com.freshdigitable.yttt.data.model

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchStreamSchedule
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.local.db.Converter
import com.freshdigitable.yttt.data.source.local.db.IdConverter
import kotlinx.coroutines.flow.Flow
import java.time.Duration
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

@DatabaseView(
    viewName = "twitch_user_detail_view",
    value = "SELECT u.*, d.profile_image_url, d.views_count, d.created_at, d.description " +
        "FROM twitch_user_detail AS d " +
        "INNER JOIN twitch_user AS u ON d.user_id = u.id",
)
data class TwitchUserDetailDbView(
    @ColumnInfo("id")
    override val id: TwitchUser.Id,
    @ColumnInfo("login_name")
    override val loginName: String,
    @ColumnInfo("display_name")
    override val displayName: String,
    @ColumnInfo("description")
    override val description: String,
    @ColumnInfo("profile_image_url")
    override val profileImageUrl: String,
    @ColumnInfo("views_count")
    override val viewsCount: Int,
    @ColumnInfo("created_at")
    override val createdAt: Instant,
) : TwitchUserDetail

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
            entity = TwitchBroadcasterTable::class,
            parentColumns = ["user_id", "follower_user_id"],
            childColumns = ["user_id", "follower_user_id"],
        ),
    ],
    primaryKeys = ["user_id", "follower_user_id"],
)
class TwitchBroadcasterExpireTable(
    @ColumnInfo("user_id")
    val id: TwitchUser.Id,
    @ColumnInfo("follower_user_id", index = true)
    val followerId: TwitchUser.Id,
    @ColumnInfo("expire_at")
    val expireAt: Instant,
)

data class TwitchBroadcasterDb(
    @ColumnInfo("id")
    override val id: TwitchUser.Id,
    @ColumnInfo("login_name")
    override val loginName: String,
    @ColumnInfo("display_name")
    override val displayName: String,
    @ColumnInfo("followed_at")
    override val followedAt: Instant
) : TwitchBroadcaster

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
    value = "SELECT s.*, u.display_name AS user_display_name, u.login_name AS user_login_name" +
        " FROM twitch_stream AS s INNER JOIN twitch_user AS u ON u.id = s.user_id",
)
data class TwitchStreamDbView(
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
        parentColumn = "user_id",
        entityColumn = "user_id"
    )
    override val segments: List<TwitchStreamScheduleTable>?,
    @Embedded("user_")
    override val broadcaster: TwitchUserTable,
    @Embedded
    override val vacation: TwitchChannelVacationSchedule?,
) : TwitchChannelSchedule

class TwitchUserIdConverter : IdConverter<TwitchUser.Id>(createObject = { TwitchUser.Id(it) })
class TwitchStreamScheduleIdConverter :
    IdConverter<TwitchChannelSchedule.Stream.Id>(createObject = { TwitchChannelSchedule.Stream.Id(it) })

class TwitchStreamIdConverter : IdConverter<TwitchStream.Id>(createObject = { TwitchStream.Id(it) })
class CsvConverter : Converter<String, List<@JvmSuppressWildcards String>>(
    serialize = { it.joinToString(separator = ",") },
    createObject = { it.split(",") },
)

@Dao
interface TwitchDao {
    @Transaction
    suspend fun setMe(me: TwitchUserDetail) {
        addUserDetails(listOf(me))
        setMeEntity(TwitchAuthorizedUserTable(me.id))
    }

    @Insert
    suspend fun setMeEntity(me: TwitchAuthorizedUserTable)

    @Query("SELECT u.* FROM twitch_auth_user AS a INNER JOIN twitch_user_detail_view AS u ON a.user_id = u.id LIMIT 1")
    suspend fun findMe(): TwitchUserDetailDbView?

    @Transaction
    suspend fun addUserDetails(
        users: Collection<TwitchUserDetail>,
        expiredAt: Instant = Instant.now() + MAX_AGE_USER_DETAIL,
    ) {
        val expires = users.map { TwitchUserDetailExpireTable(it.id, expiredAt) }
        val details = users.map { it.toTable() }
        addUserDetailExpireEntities(expires)
        addUserDetailEntities(details)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserDetailEntities(details: Collection<TwitchUserDetailTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserDetailExpireEntities(expires: Collection<TwitchUserDetailExpireTable>)

    @Query(
        "SELECT v.* FROM (SELECT * FROM twitch_user_detail_view WHERE id IN (:ids)) AS v " +
            "INNER JOIN (SELECT * FROM twitch_user_detail_expire WHERE :current < expired_at) AS e ON v.id = e.user_id"
    )
    suspend fun findUserDetail(
        ids: Collection<TwitchUser.Id>,
        current: Instant = Instant.now(),
    ): List<TwitchUserDetailDbView>

    @Query("SELECT * FROM twitch_user WHERE id = :id")
    suspend fun findUser(id: TwitchUser.Id): TwitchUserTable?

    @Transaction
    suspend fun addBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
        expiredAt: Instant = Instant.now() + MAX_AGE_BROADCASTER,
    ) {
        addBroadcasterEntities(broadcasters.map { it.toTable(followerId) })
        addBroadcasterExpireEntities(broadcasters.map {
            TwitchBroadcasterExpireTable(it.id, followerId, expiredAt)
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBroadcasterExpireEntities(expires: Collection<TwitchBroadcasterExpireTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBroadcasterEntities(broadcasters: Collection<TwitchBroadcasterTable>)

    @Query(
        "SELECT u.*, b.followed_at FROM " +
            "(SELECT * FROM twitch_broadcaster AS bb " +
            " INNER JOIN twitch_broadcaster_expire AS e " +
            " ON e.user_id = bb.user_id AND e.follower_user_id = bb.follower_user_id " +
            " WHERE :current < e.expire_at) AS b " +
            "INNER JOIN twitch_user AS u ON b.user_id = u.id " +
            "WHERE b.follower_user_id = :id"
    )
    suspend fun findBroadcastersByFollowerId(
        id: TwitchUser.Id,
        current: Instant = Instant.now(),
    ): List<TwitchBroadcasterDb>

    @Transaction
    suspend fun setBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
    ) {
        remoteBroadcasterExpireByFollowerId(followerId)
        removeBroadcastersByFollowerId(followerId)
        addBroadcasters(followerId, broadcasters)
    }

    @Query("DELETE FROM twitch_broadcaster WHERE follower_user_id = :followerId")
    suspend fun removeBroadcastersByFollowerId(followerId: TwitchUser.Id)

    @Query("DELETE FROM twitch_broadcaster_expire WHERE follower_user_id = :followerId")
    suspend fun remoteBroadcasterExpireByFollowerId(followerId: TwitchUser.Id)

    @Transaction
    suspend fun setChannelSchedules(schedule: Collection<TwitchChannelSchedule>) {
        val userIds = schedule.map { it.broadcaster.id }.toSet()
        val streams = schedule.map { it.toStreamScheduleTable() }.flatten()
        val vacations = schedule.map { it.toVacationScheduleTable() }
        removeChannelStreamSchedulesByUserIds(userIds)
        removeChannelVacationSchedulesByUserIds(userIds)
        addChannelStreamSchedules(streams)
        addChannelVacationSchedules(vacations)
    }

    @Query("DELETE FROM twitch_channel_schedule_stream WHERE user_id IN (:ids)")
    suspend fun removeChannelStreamSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

    @Query("DELETE FROM twitch_channel_schedule_vacation WHERE user_id IN (:ids)")
    suspend fun removeChannelVacationSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelStreamSchedules(streams: Collection<TwitchStreamScheduleTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelVacationSchedules(streams: Collection<TwitchChannelVacationScheduleTable>)

    @Transaction
    @Query(
        "SELECT s.*, u.display_name AS user_display_name, u.login_name AS user_login_name " +
            "FROM twitch_channel_schedule_vacation AS s " +
            "INNER JOIN twitch_user AS u ON s.user_id = u.id " +
            "WHERE u.id = :id"
    )
    suspend fun findChannelSchedule(id: TwitchUser.Id): List<TwitchChannelScheduleDb>

    @Transaction
    @Query(
        "SELECT s.*, u.display_name AS user_display_name, u.login_name AS user_login_name " +
            "FROM twitch_channel_schedule_vacation AS s " +
            "INNER JOIN twitch_user AS u ON s.user_id = u.id"
    )
    fun watchChannelSchedule(): Flow<List<TwitchChannelScheduleDb>>

    @Transaction
    suspend fun findStreamSchedule(id: TwitchChannelSchedule.Stream.Id): TwitchVideo<TwitchChannelSchedule.Stream.Id>? {
        val schedule = findStreamScheduleEntity(id) ?: return null
        val user = findUser(schedule.userId) ?: return null
        return TwitchStreamSchedule(user, schedule)
    }

    @Query("SELECT * FROM twitch_channel_schedule_stream AS s WHERE s.id = :id")
    suspend fun findStreamScheduleEntity(id: TwitchChannelSchedule.Stream.Id): TwitchStreamScheduleTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addStreams(streams: Collection<TwitchStreamTable>)

    @Transaction
    suspend fun setStreams(streams: Collection<TwitchStreamTable>) {
        removeAllStreams()
        addStreams(streams)
    }

    @Query("SELECT * FROM twitch_stream_view AS v WHERE v.id = :id")
    suspend fun findStream(id: TwitchStream.Id): TwitchStreamDbView

    @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
    fun watchStream(): Flow<List<TwitchStreamDbView>>

    @Query("DELETE FROM twitch_stream")
    suspend fun removeAllStreams()

    @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
    suspend fun findAllStreams(): List<TwitchStreamDbView>

    companion object {
        private val MAX_AGE_BROADCASTER = Duration.ofHours(12)
        private val MAX_AGE_USER_DETAIL = Duration.ofDays(1)
    }
}

private fun TwitchUserDetail.toTable(): TwitchUserDetailTable =
    TwitchUserDetailTable(id, profileImageUrl, viewsCount, createdAt, description)

private fun TwitchChannelSchedule.toStreamScheduleTable(): List<TwitchStreamScheduleTable> =
    segments?.map {
        TwitchStreamScheduleTable(
            id = it.id,
            title = it.title,
            startTime = it.startTime,
            endTime = it.endTime,
            canceledUntil = it.canceledUntil,
            category = it.category?.toTable(),
            isRecurring = it.isRecurring,
            userId = broadcaster.id,
        )
    } ?: emptyList()

private fun TwitchChannelSchedule.StreamCategory.toTable(): TwitchStreamCategory =
    TwitchStreamCategory(id, name)

private fun TwitchChannelSchedule.toVacationScheduleTable(): TwitchChannelVacationScheduleTable =
    TwitchChannelVacationScheduleTable(
        userId = broadcaster.id,
        vacation = vacation?.toTable(),
    )

private fun TwitchChannelSchedule.Vacation.toTable(): TwitchChannelVacationSchedule =
    TwitchChannelVacationSchedule(startTime, endTime)

private fun TwitchBroadcaster.toTable(followerId: TwitchUser.Id): TwitchBroadcasterTable =
    TwitchBroadcasterTable(
        id = id,
        followerId = followerId,
        followedAt = followedAt,
    )

fun TwitchStream.toTable(): TwitchStreamTable = TwitchStreamTable(
    userId = user.id,
    title = title,
    id = id,
    gameId = gameId,
    gameName = gameName,
    isMature = isMature,
    language = language,
    startedAt = startedAt,
    tags = tags,
    thumbnailUrlBase = thumbnailUrlBase,
    type = type,
    viewCount = viewCount,
)
