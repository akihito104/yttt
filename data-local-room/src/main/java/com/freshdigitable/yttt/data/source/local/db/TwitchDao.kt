package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import javax.inject.Inject

internal class TwitchDao @Inject constructor(
    private val db: AppDatabase,
    private val userDao: TwitchUserDaoImpl,
    private val scheduleDao: TwitchScheduleDaoImpl,
    private val streamDao: TwitchStreamDaoImpl,
) : TwitchUserDao by userDao, TwitchScheduleDao by scheduleDao, TwitchStreamDao by streamDao {
    suspend fun setMe(me: Updatable<TwitchUserDetail>) = db.withTransaction {
        addUserDetails(listOf(me))
        setMeEntity(TwitchAuthorizedUserTable(me.item.id))
    }

    suspend fun addUserDetails(
        users: Collection<Updatable<TwitchUserDetail>>,
    ) = db.withTransaction {
        val details = users.map { it.item.toTable() }
        val expires = users.map {
            TwitchUserDetailExpireTable(it.item.id, it.cacheControl.toDb())
        }
        addUsers(users.map { (it.item as TwitchUser).toTable() })
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

    suspend fun findFollowingsByFollowerId(userId: TwitchUser.Id): Updatable<TwitchFollowings> =
        db.withTransaction {
            val items = findBroadcastersByFollowerId(userId)
            val expires = findByFollowerUserId(userId)
            TwitchFollowings.create(userId, items, expires?.cacheControl)
        }

    suspend fun replaceAllBroadcasters(
        followings: Updatable<TwitchFollowings>,
    ) = db.withTransaction {
        removeBroadcastersByFollowerId(followings.item.followerId)
        addBroadcasters(followings.item.followerId, followings.item.followings)
        val expires = TwitchBroadcasterExpireTable(
            followings.item.followerId,
            followings.cacheControl.toDb()
        )
        addBroadcasterExpireEntity(expires)
    }

    suspend fun replaceChannelSchedules(
        broadcasterId: TwitchUser.Id,
        updatable: Updatable<TwitchChannelSchedule?>,
    ) = db.withTransaction {
        val schedule = updatable.item
        val streams = schedule?.segments?.map { it.toStreamScheduleTable(broadcasterId) }
        val vacations = schedule?.vacation.toVacationScheduleTable(broadcasterId)
        val expire = TwitchChannelScheduleExpireTable(broadcasterId, updatable.cacheControl.toDb())
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
    ): Updatable<TwitchChannelSchedule?> = db.withTransaction {
        val vacation = findChannelVacationUpdatable(userId)
            ?: return@withTransaction Updatable.create(null, CacheControl.EMPTY)
        val schedule = findStreamScheduleByUserId(userId)
        object : TwitchChannelSchedule {
            override val segments: List<TwitchChannelSchedule.Stream>? get() = schedule
            override val broadcaster: TwitchUser get() = vacation.user
            override val vacation: TwitchChannelSchedule.Vacation? get() = vacation.vacation
        }.toUpdatable(vacation.cacheControl)
    }

    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
        findCategoryById(id)

    suspend fun upsertCategory(category: Collection<TwitchCategory>) {
        upsertCategories(category.map(TwitchCategory::toTable))
    }

    suspend fun findStreamByMe(me: TwitchUser.Id): Updatable<TwitchStreams> = db.withTransaction {
        val expire = findStreamExpire(me)
        val s = findAllStreams()
        TwitchStreams.create(me, s, expire?.cacheControl)
    }

    suspend fun replaceAllStreams(streams: Updatable<out TwitchStreams>) = db.withTransaction {
        db.twitchStreamDao.deleteTable()
        setStreamExpire(
            TwitchStreamExpireTable(streams.item.followerId, streams.cacheControl.toDb())
        )
        addUsers(streams.item.streams.map { it.user.toTable() })
        addCategories(streams.item.streams.map { TwitchCategoryTable(it.gameId, it.gameName) })
        addStreams(streams.item.streams.map { it.toTable() })
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

internal interface TwitchDaoProviders : TwitchUserDaoProviders, TwitchStreamDaoProviders,
    TwitchScheduleDaoProviders, TwitchPageSourceDaoProviders
