package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable
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
    suspend fun setMe(me: TwitchUserDetail) = db.withTransaction {
        addUserDetails(listOf(me))
        setMeEntity(TwitchAuthorizedUserTable(me.id))
    }

    suspend fun addUserDetails(users: Collection<TwitchUserDetail>) = db.withTransaction {
        val details = users.map { it.toTable() }
        val expires = users.map { TwitchUserDetailExpireTable(it.id, it.fetchedAt, it.maxAge) }
        addUsers(users.map { (it as TwitchUser).toTable() })
        addUserDetailEntities(details)
        addUserDetailExpireEntities(expires)
    }

    private suspend fun removeUser(id: Collection<TwitchUser.Id>) = db.withTransaction {
        removeDetailExpireEntities(id)
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
            TwitchFollowings.create(userId, items, expires?.fetchedAt, expires?.maxAge)
        }

    suspend fun replaceAllBroadcasters(followings: TwitchFollowings) = db.withTransaction {
        removeBroadcastersByFollowerId(followings.followerId)
        addBroadcasters(followings.followerId, followings.followings)
        val expires = TwitchBroadcasterExpireTable(
            followings.followerId,
            followings.fetchedAt,
            followings.maxAge,
        )
        addBroadcasterExpireEntity(expires)
    }

    suspend fun replaceChannelSchedules(
        broadcasterId: TwitchUser.Id,
        updatable: TwitchChannelScheduleUpdatable,
    ) = db.withTransaction {
        val schedule = updatable.schedule
        val streams = schedule?.segments?.map { it.toStreamScheduleTable(broadcasterId) }
        val vacations = schedule?.vacation.toVacationScheduleTable(broadcasterId)
        val expire =
            TwitchChannelScheduleExpireTable(broadcasterId, updatable.fetchedAt, updatable.maxAge)
        val category = schedule?.segments?.mapNotNull { it.category?.toTable() }
        if (!category.isNullOrEmpty()) addCategories(category)

        removeChannelSchedules(setOf(broadcasterId))
        if (!streams.isNullOrEmpty()) addChannelStreamSchedules(streams)
        addChannelVacationSchedules(setOf(vacations))
        addChannelScheduleExpireEntity(setOf(expire))
    }

    private suspend fun removeChannelSchedules(id: Collection<TwitchUser.Id>) = db.withTransaction {
        removeChannelStreamSchedulesByUserIds(id)
        removeChannelScheduleExpireEntity(id)
        removeChannelVacationSchedulesByUserIds(id)
    }

    suspend fun findStreamSchedule(
        id: TwitchChannelSchedule.Stream.Id
    ): TwitchLiveVideo<TwitchChannelSchedule.Stream.Id>? = findLiveSchedule(id)

    suspend fun findChannelSchedule(
        userId: TwitchUser.Id,
    ): TwitchChannelSchedule? = db.withTransaction {
        val user = findUserDetail(setOf(userId), Instant.EPOCH).firstOrNull()
            ?: return@withTransaction null
        val vacation = findVacationById(userId)
        val schedule = findStreamScheduleByUserId(userId)
        TwitchChannelScheduleDb(
            segments = schedule,
            broadcaster = user,
            vacation = vacation?.vacation,
        )
    }

    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
        findCategoryById(id)

    suspend fun upsertCategory(category: Collection<TwitchCategory>) {
        upsertCategories(category.map(TwitchCategory::toTable))
    }

    suspend fun findStreamByMe(me: TwitchUser.Id): TwitchStreams = db.withTransaction {
        val expire = findStreamExpire(me)
        val s = findAllStreams()
        TwitchStreams.create(me, s, expire?.fetchedAt, expire?.maxAge)
    }

    suspend fun replaceAllStreams(streams: TwitchStreams) = db.withTransaction {
        db.twitchStreamDao.deleteTable()
        setStreamExpire(
            TwitchStreamExpireTable(streams.followerId, streams.fetchedAt, streams.maxAge)
        )
        addUsers(streams.streams.map { it.user.toTable() })
        addCategories(streams.streams.map { TwitchCategoryTable(it.gameId, it.gameName) })
        addStreams(streams.streams.map { it.toTable() })
    }

    suspend fun cleanUpByUserId(id: Collection<TwitchUser.Id>) = db.withTransaction {
        val isFollowed = isBroadcasterFollowed(id.toSet())
        val removed = id.associateWith { isFollowed[it] == true }.filter { !it.value }.keys
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

private fun TwitchChannelSchedule.Stream.toStreamScheduleTable(
    broadcasterId: TwitchUser.Id,
): TwitchStreamScheduleTable = TwitchStreamScheduleTable(
    id = id,
    title = title,
    startTime = startTime,
    endTime = endTime,
    canceledUntil = canceledUntil,
    categoryId = category?.id,
    isRecurring = isRecurring,
    userId = broadcasterId,
)

private fun TwitchCategory.toTable(): TwitchCategoryTable =
    TwitchCategoryTable(id, name, artUrlBase, igdbId)

private fun TwitchChannelSchedule.Vacation?.toVacationScheduleTable(
    broadcasterId: TwitchUser.Id,
): TwitchChannelVacationScheduleTable = TwitchChannelVacationScheduleTable(
    userId = broadcasterId,
    vacation = if (this == null) null else TwitchChannelVacationSchedule(startTime, endTime),
)

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
