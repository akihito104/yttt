package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
        schedule: Collection<TwitchChannelSchedule>,
        expiredAt: Instant,
    ) = db.withTransaction {
        val userIds = schedule.map { it.broadcaster.id }.toSet()
        val streams = schedule.map { it.toStreamScheduleTable() }.flatten()
        val vacations = schedule.map { it.toVacationScheduleTable() }
        val expire = userIds.map { TwitchChannelScheduleExpireTable(it, expiredAt) }
        removeChannelSchedules(userIds)
        addChannelStreamSchedules(streams)
        addChannelVacationSchedules(vacations)
        addChannelScheduleExpireEntity(expire)
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
    ): TwitchLiveVideo<TwitchChannelSchedule.Stream.Id>? = db.withTransaction {
        val schedule = findStreamScheduleEntity(id) ?: return@withTransaction null
        val user = findUserDetail(setOf(schedule.userId), Instant.EPOCH).first()
        val category = schedule.category?.let { _categories.value[it.id] }
        TwitchLiveSchedule.create(user, schedule, category?.artUrlBase)
    }

    private val _categories = MutableStateFlow<Map<TwitchCategory.Id, TwitchCategory>>(emptyMap())
    val categories: Flow<Map<TwitchCategory.Id, TwitchCategory>> get() = _categories
    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
        id.mapNotNull { _categories.value[it] }

    suspend fun addCategory(category: Collection<TwitchCategory>) {
        _categories.update { c -> c + category.associateBy { it.id } }
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
            category = it.category?.toTable(),
            isRecurring = it.isRecurring,
            userId = broadcaster.id,
        )
    } ?: emptyList()

private fun TwitchCategory.toTable(): TwitchStreamCategory = TwitchStreamCategory(id, name)

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

internal interface TwitchDaoProviders : TwitchUserDaoProviders, TwitchStreamDaoProviders,
    TwitchScheduleDaoProviders, TwitchPageSourceDaoProviders
