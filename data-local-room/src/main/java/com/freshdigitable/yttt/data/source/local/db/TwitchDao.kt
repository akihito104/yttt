package com.freshdigitable.yttt.data.source.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreamSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
internal interface TwitchDao {
    @Transaction
    suspend fun setMe(me: TwitchUserDetail, expiredAt: Instant) {
        addUserDetails(listOf(me), expiredAt)
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
        expiredAt: Instant,
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
        current: Instant,
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
        current: Instant,
    ): List<TwitchBroadcasterDb>

    @Transaction
    suspend fun replaceAllBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
        expiredAt: Instant,
    ) {
        removeBroadcastersByFollowerId(followerId)
        addBroadcasters(followerId, broadcasters)
        addBroadcasterExpireEntity(TwitchBroadcasterExpireTable(followerId, expiredAt))
    }

    @Query("DELETE FROM twitch_broadcaster WHERE follower_user_id = :followerId")
    suspend fun removeBroadcastersByFollowerId(followerId: TwitchUser.Id)

    @Transaction
    suspend fun replaceChannelSchedules(
        schedule: Collection<TwitchChannelSchedule>,
        expiredAt: Instant,
    ) {
        val userIds = schedule.map { it.broadcaster.id }.toSet()
        val streams = schedule.map { it.toStreamScheduleTable() }.flatten()
        val vacations = schedule.map { it.toVacationScheduleTable() }
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
        current: Instant,
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
    suspend fun replaceAllStreams(
        me: TwitchUser.Id,
        streams: Collection<TwitchStream>,
        expiredAt: Instant,
    ) {
        removeAllStreams()
        setStreamExpire(TwitchStreamExpireTable(me, expiredAt))
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
