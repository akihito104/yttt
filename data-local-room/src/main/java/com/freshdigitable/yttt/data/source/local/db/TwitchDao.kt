package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import java.time.Instant
import javax.inject.Inject

internal class TwitchDao @Inject constructor(
    private val db: AppDatabase,
    private val userDao: TwitchUserDaoImpl,
    private val scheduleDao: TwitchScheduleDaoImpl,
    private val streamDao: TwitchStreamDaoImpl,
) : TwitchUserDao by userDao, TwitchScheduleDao by scheduleDao, TwitchStreamDao by streamDao {
    suspend fun setMe(me: TwitchUserDetail, expiredAt: Instant) = db.withTransaction {
        addUserDetails(listOf(me), expiredAt)
        setMeEntity(TwitchAuthorizedUserTable(me.id))
    }

    suspend fun addUserDetails(
        users: Collection<TwitchUserDetail>,
        expiredAt: Instant,
    ) = db.withTransaction {
        val details = users.map { it.toTable() }
        val expires = users.map { TwitchUserDetailExpireTable(it.id, expiredAt) }
        addUsers(users.map { (it as TwitchUser).toTable() })
        addUserDetailEntities(details)
        addUserDetailExpireEntities(expires)
    }

    private suspend fun removeUser(id: Collection<TwitchUser.Id>) = db.withTransaction {
        removeUserDetail(id)
        removeUsers(id)
    }

    private suspend fun addBroadcasters(
        followerId: TwitchUser.Id,
        broadcasters: Collection<TwitchBroadcaster>,
    ) = db.withTransaction {
        addUsers(broadcasters.map { TwitchUserTable(it.id, it.loginName, it.displayName) })
        addBroadcasterEntities(broadcasters.map { it.toTable(followerId) })
    }

    suspend fun findFollowingsByFollowerId(userId: TwitchUser.Id): TwitchFollowings =
        db.withTransaction {
            val items = findBroadcastersByFollowerId(userId)
            val expires = findByFollowerUserId(userId)
            val updatableAt = expires?.expireAt ?: Instant.EPOCH
            TwitchFollowings.create(userId, items, updatableAt)
        }

    suspend fun replaceAllBroadcasters(followings: TwitchFollowings) = db.withTransaction {
        removeBroadcastersByFollowerId(followings.followerId)
        addBroadcasters(followings.followerId, followings.followings)
        val expires = TwitchBroadcasterExpireTable(followings.followerId, followings.updatableAt)
        addBroadcasterExpireEntity(expires)
    }

    suspend fun replaceChannelSchedules(
        schedule: TwitchChannelSchedule,
        expiredAt: Instant,
    ) = db.withTransaction {
        val userIds = schedule.broadcaster.id
        val streams = schedule.toStreamScheduleTable()
        val category = schedule.segments?.mapNotNull { it.category?.toTable() }
        val vacations = schedule.toVacationScheduleTable()
        val expire = TwitchChannelScheduleExpireTable(userIds, expiredAt)
        removeChannelSchedules(setOf(userIds))
        if (!category.isNullOrEmpty()) addCategory(category)
        if (streams.isNotEmpty()) addChannelStreamSchedules(streams)
        addChannelVacationSchedules(setOf(vacations))
        addChannelScheduleExpireEntity(setOf(expire))
    }

    suspend fun removeChannelSchedulesByBroadcasterId(id: Collection<TwitchUser.Id>) =
        db.withTransaction {
            val isFollowed = isBroadcasterFollowed(id.toSet())
            val removed = id.associateWith { isFollowed[it] ?: false }.filter { !it.value }.keys
            removeChannelSchedules(removed)
        }

    private suspend fun removeChannelSchedules(id: Collection<TwitchUser.Id>) = db.withTransaction {
        removeChannelStreamSchedulesByUserIds(id)
        removeChannelScheduleExpireEntity(id)
        removeChannelVacationSchedulesByUserIds(id)
    }

    suspend fun updateChannelScheduleExpireEntity(userId: TwitchUser.Id, expiredAt: Instant) =
        db.withTransaction {
            val vacations = listOf(TwitchChannelVacationScheduleTable(userId, null))
            addChannelVacationSchedules(vacations)
            val entity = listOf(TwitchChannelScheduleExpireTable(userId, expiredAt))
            addChannelScheduleExpireEntity(entity)
        }

    suspend fun findStreamSchedule(
        id: TwitchChannelSchedule.Stream.Id
    ): TwitchLiveVideo<TwitchChannelSchedule.Stream.Id>? = findLiveSchedule(id)

    suspend fun findChannelSchedule(
        userId: TwitchUser.Id,
        current: Instant,
    ): TwitchChannelSchedule? = db.withTransaction {
        val user = findUserDetail(setOf(userId), Instant.EPOCH).firstOrNull()
            ?: return@withTransaction null
        val vacation = findVacationById(userId)
        val schedule = findStreamScheduleByUserId(userId, current)
        TwitchChannelScheduleDb(
            segments = schedule,
            broadcaster = user,
            vacation = vacation?.vacation,
        )
    }

    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
        findCategoryById(id)

    suspend fun addCategory(category: Collection<TwitchCategory>) {
        addCategories(category.map(TwitchCategory::toTable))
    }

    suspend fun findStreamByMe(me: TwitchUser.Id): TwitchStreams = db.withTransaction {
        val expiredAt = findStreamExpire(me)?.expiredAt
        val s = findAllStreams()
        TwitchStreams.create(me, s, expiredAt ?: Instant.EPOCH)
    }

    suspend fun replaceAllStreams(streams: TwitchStreams) = db.withTransaction {
        db.twitchStreamDao.deleteTable()
        setStreamExpire(TwitchStreamExpireTable(streams.followerId, streams.updatableAt))
        addUsers(streams.streams.map { it.user.toTable() })
        addCategories(streams.streams.map { TwitchCategoryTable(it.gameId, it.gameName) })
        addStreams(streams.streams.map { it.toTable() })
    }

    suspend fun cleanUpByUserId(id: Collection<TwitchUser.Id>) = db.withTransaction {
        val isFollowed = isBroadcasterFollowed(id.toSet())
        val removed = id.associateWith { isFollowed[it] ?: false }.filter { !it.value }.keys
        removeChannelSchedules(removed)
        val me = findAuthorizedUser(removed)
        removeUser(removed - me.map { it.userId }.toSet())
    }

    override suspend fun deleteTable() = db.withTransaction {
        db.deferForeignKeys()
        listOf(userDao, streamDao, scheduleDao).forEach { it.deleteTable() }
    }
}

private fun TwitchUser.toTable(): TwitchUserTable = TwitchUserTable(id, loginName, displayName)
private fun TwitchUserDetail.toTable(): TwitchUserDetailTable =
    TwitchUserDetailTable(id, profileImageUrl, createdAt, description)

private fun TwitchChannelSchedule.toStreamScheduleTable(): List<TwitchStreamScheduleTable> =
    segments?.map {
        TwitchStreamScheduleTable(
            id = it.id,
            title = it.title,
            startTime = it.startTime,
            endTime = it.endTime,
            canceledUntil = it.canceledUntil,
            categoryId = it.category?.id,
            isRecurring = it.isRecurring,
            userId = broadcaster.id,
        )
    } ?: emptyList()

private fun TwitchCategory.toTable(): TwitchCategoryTable =
    TwitchCategoryTable(id, name, artUrlBase, igdbId)

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
    isMature = isMature,
    language = language,
    startedAt = startedAt,
    tags = tags,
    thumbnailUrlBase = thumbnailUrlBase,
    type = type,
    viewCount = viewCount,
)

private class TwitchChannelScheduleDb(
    override val segments: List<TwitchChannelSchedule.Stream>?,
    override val broadcaster: TwitchUserDetailDbView,
    override val vacation: TwitchChannelVacationSchedule?,
) : TwitchLiveChannelSchedule

internal interface TwitchDaoProviders : TwitchUserDaoProviders, TwitchStreamDaoProviders,
    TwitchScheduleDaoProviders, TwitchPageSourceDaoProviders
