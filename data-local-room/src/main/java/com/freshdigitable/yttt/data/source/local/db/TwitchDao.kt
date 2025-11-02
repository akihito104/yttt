package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import javax.inject.Inject

internal class TwitchDao @Inject constructor(
    private val db: AppDatabase,
    private val userDao: TwitchUserDaoImpl,
    private val scheduleDao: TwitchScheduleDaoImpl,
    private val streamDao: TwitchStreamDaoImpl,
) : TwitchUserDao by userDao, TwitchScheduleDao by scheduleDao, TwitchStreamDao by streamDao {
    suspend fun findFollowingsByFollowerId(userId: TwitchUser.Id): Updatable<TwitchFollowings> = db.withTransaction {
        val items = findBroadcastersByFollowerId(userId)
        val expires = findByFollowerUserId(userId)
        TwitchFollowings.create(userId, items, expires?.cacheControl)
    }

    suspend fun findStreamSchedule(
        id: TwitchChannelSchedule.Stream.Id,
    ): TwitchLiveVideo<TwitchChannelSchedule.Stream.Id>? = findLiveSchedule(id)

    suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> = findCategoryById(id)

    suspend fun findStreamByMe(me: TwitchUser.Id): Updatable<TwitchStreams> = db.withTransaction {
        val expire = findStreamExpire(me)
        val s = findAllStreams()
        TwitchStreams.create(me, s, expire?.cacheControl)
    }

    suspend fun replaceAllStreams(streams: Updatable<out TwitchStreams>) = db.withTransaction {
        db.twitchStreamDao.deleteTable()
        setStreamExpireEntity(streams)
        addUserEntities(streams.item.streams.map { it.user })
        addCategoryEntities(streams.item.streams)
        addStreamEntities(streams.item.streams)
    }

    suspend fun cleanUpByUserId(id: Collection<TwitchUser.Id>) = db.withTransaction {
        val isFollowed = isBroadcasterFollowed(id.toSet()).associate { it.userId to it.isFollowed }
        val removed = id.associateWith { isFollowed[it] == true }.filter { !it.value }.keys
        removeChannelScheduleEntities(removed)
        val me = findAuthorizedUser(removed)
        removeUser(removed - me.map { it.userId }.toSet())
    }

    private suspend fun removeUser(id: Collection<TwitchUser.Id>) = db.withTransaction {
        removeDetailExpireEntities(id)
        removeUserDetail(id)
        removeUsers(id)
    }

    override suspend fun deleteTable() = db.withTransaction {
        db.deferForeignKeys()
        listOf(userDao, streamDao, scheduleDao).forEach { it.deleteTable() }
    }
}

internal interface TwitchDaoProviders :
    TwitchUserDaoProviders,
    TwitchStreamDaoProviders,
    TwitchScheduleDaoProviders,
    TwitchPageSourceDaoProviders
