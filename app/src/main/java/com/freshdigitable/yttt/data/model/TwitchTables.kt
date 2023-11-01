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

    @Query(
        "SELECT u.* FROM twitch_auth_user AS a " +
            "INNER JOIN (${TwitchUserDetailDbView.SQL_USER_DETAIL}) AS u ON a.user_id = u.id LIMIT 1"
    )
    suspend fun findMe(): TwitchUserDetailDbView?

    @Transaction
    suspend fun addUserDetails(
        users: Collection<TwitchUserDetail>,
        expiredAt: Instant = Instant.now() + MAX_AGE_USER_DETAIL,
    ) {
        val details = users.map { it.toTable() }
        val expires = users.map { TwitchUserDetailExpireTable(it.id, expiredAt) }
        addUsers(users.map { (it as TwitchUser).toTable() })
        addUserDetailEntities(details)
        addUserDetailExpireEntities(expires)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserDetailEntities(details: Collection<TwitchUserDetailTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserDetailExpireEntities(expires: Collection<TwitchUserDetailExpireTable>)

    @Query(
        "SELECT v.* FROM (SELECT * FROM (${TwitchUserDetailDbView.SQL_USER_DETAIL}) WHERE id IN (:ids)) AS v " +
            "INNER JOIN (SELECT * FROM twitch_user_detail_expire WHERE :current < expired_at) AS e ON v.id = e.user_id"
    )
    suspend fun findUserDetail(
        ids: Collection<TwitchUser.Id>,
        current: Instant = Instant.now(),
    ): List<TwitchUserDetailDbView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUsers(user: Collection<TwitchUserTable>)

    @Query("SELECT * FROM twitch_user WHERE id = :id")
    suspend fun findUser(id: TwitchUser.Id): TwitchUserTable?

    @Transaction
    suspend fun addBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
    ) {
        addUsers(broadcasters.map { TwitchUserTable(it.id, it.loginName, it.displayName) })
        addBroadcasterEntities(broadcasters.map { it.toTable(followerId) })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBroadcasterExpireEntity(expires: TwitchBroadcasterExpireTable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBroadcasterEntities(broadcasters: Collection<TwitchBroadcasterTable>)

    @Query(
        "SELECT u.*, b.followed_at FROM " +
            "(SELECT bb.* FROM twitch_broadcaster AS bb " +
            " INNER JOIN twitch_broadcaster_expire AS e ON e.follower_user_id = bb.follower_user_id " +
            " WHERE :current < e.expire_at) AS b " +
            "INNER JOIN twitch_user AS u ON b.user_id = u.id " +
            "WHERE b.follower_user_id = :id"
    )
    suspend fun findBroadcastersByFollowerId(
        id: TwitchUser.Id,
        current: Instant = Instant.now(),
    ): List<TwitchBroadcasterDb>

    @Transaction
    suspend fun replaceAllBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
        expiredAt: Instant = Instant.now() + MAX_AGE_BROADCASTER,
    ) {
        removeBroadcastersByFollowerId(followerId)
        addBroadcasters(followerId, broadcasters)
        addBroadcasterExpireEntity(TwitchBroadcasterExpireTable(followerId, expiredAt))
    }

    @Query("DELETE FROM twitch_broadcaster WHERE follower_user_id = :followerId")
    suspend fun removeBroadcastersByFollowerId(followerId: TwitchUser.Id)

    @Transaction
    suspend fun replaceChannelSchedules(schedule: Collection<TwitchChannelSchedule>) {
        val userIds = schedule.map { it.broadcaster.id }.toSet()
        val streams = schedule.map { it.toStreamScheduleTable() }.flatten()
        val vacations = schedule.map { it.toVacationScheduleTable() }
        val expiredAt = Instant.now() + MAX_AGE_CHANNEL_SCHEDULE
        val expire = userIds.map { TwitchChannelScheduleExpireTable(it, expiredAt) }
        removeChannelStreamSchedulesByUserIds(userIds)
        removeChannelScheduleExpireEntity(userIds)
        removeChannelVacationSchedulesByUserIds(userIds)
        addChannelStreamSchedules(streams)
        addChannelVacationSchedules(vacations)
        addChannelScheduleExpireEntity(expire)
    }

    @Query("DELETE FROM twitch_channel_schedule_stream WHERE user_id IN (:ids)")
    suspend fun removeChannelStreamSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

    @Query("DELETE FROM twitch_channel_schedule_vacation WHERE user_id IN (:ids)")
    suspend fun removeChannelVacationSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

    @Query("DELETE FROM twitch_channel_schedule_stream WHERE id IN (:ids)")
    suspend fun removeChannelStreamSchedulesByIds(ids: Collection<TwitchChannelSchedule.Stream.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelStreamSchedules(streams: Collection<TwitchStreamScheduleTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelVacationSchedules(streams: Collection<TwitchChannelVacationScheduleTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelScheduleExpireEntity(schedule: Collection<TwitchChannelScheduleExpireTable>)

    @Query("DELETE FROM twitch_channel_schedule_expire WHERE user_id IN (:id)")
    suspend fun removeChannelScheduleExpireEntity(id: Collection<TwitchUser.Id>)

    @Transaction
    @Query(
        "SELECT s.vacation_start, s.vacation_end, ${TwitchUserDetailDbView.SQL_EMBED_ALIAS} " +
            "FROM (SELECT ss.* FROM twitch_channel_schedule_vacation AS ss " +
            " INNER JOIN twitch_channel_schedule_expire AS e ON ss.user_id = e.user_id " +
            " WHERE :current < e.expired_at " +
            ") AS s " +
            "INNER JOIN (${TwitchUserDetailDbView.SQL_USER_DETAIL}) AS u ON s.user_id = u.id " +
            "WHERE u.id = :id"
    )
    suspend fun findChannelSchedule(
        id: TwitchUser.Id,
        current: Instant = Instant.now(),
    ): List<TwitchChannelScheduleDb>

    @Transaction
    @Query(
        "SELECT s.vacation_start, s.vacation_end, ${TwitchUserDetailDbView.SQL_EMBED_ALIAS} " +
            "FROM twitch_channel_schedule_vacation AS s " +
            "INNER JOIN (${TwitchUserDetailDbView.SQL_USER_DETAIL}) AS u ON s.user_id = u.id"
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
    suspend fun replaceAllStreams(me: TwitchUser.Id, streams: Collection<TwitchStream>) {
        removeAllStreams()
        setStreamExpire(TwitchStreamExpireTable(me, Instant.now() + MAX_AGE_STREAM))
        addUsers(streams.map { it.user.toTable() })
        addStreams(streams.map { it.toTable() })
    }

    @Query("SELECT * FROM twitch_stream_view AS v WHERE v.id = :id")
    suspend fun findStream(id: TwitchStream.Id): TwitchStreamDbView

    @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
    fun watchStream(): Flow<List<TwitchStreamDbView>>

    @Query("DELETE FROM twitch_stream")
    suspend fun removeAllStreams()

    @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
    suspend fun findAllStreams(): List<TwitchStreamDbView>

    @Query("SELECT * FROM twitch_stream_expire WHERE user_id = :me")
    suspend fun findStreamExpire(me: TwitchUser.Id): TwitchStreamExpireTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStreamExpire(expiredAt: TwitchStreamExpireTable)

    companion object {
        private val MAX_AGE_BROADCASTER = Duration.ofHours(12)
        private val MAX_AGE_USER_DETAIL = Duration.ofDays(1)
        private val MAX_AGE_STREAM = Duration.ofHours(1)
        private val MAX_AGE_CHANNEL_SCHEDULE = Duration.ofDays(1)
    }
}

private fun TwitchUser.toTable(): TwitchUserTable = TwitchUserTable(id, loginName, displayName)
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

private fun TwitchStream.toTable(): TwitchStreamTable = TwitchStreamTable(
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
