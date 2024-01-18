package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLiveLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val deleteDao: Set<@JvmSuppressWildcards TableDeletable>,
    private val dateTimeProvider: DateTimeProvider,
) : TwitchLiveDataSource.Local {
    private val dao: TwitchDao = database.twitchDao
    override val onAir: Flow<List<TwitchStream>> = dao.watchStream()
    override val upcoming: Flow<List<TwitchChannelSchedule>> = dao.watchChannelSchedule()

    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            return listOfNotNull(fetchMe())
        }
        return dao.findUserDetail(ids, current = dateTimeProvider.now())
    }

    override suspend fun addUsers(users: Collection<TwitchUserDetail>) {
        dao.addUserDetails(users, expiredAt = dateTimeProvider.now() + MAX_AGE_USER_DETAIL)
    }

    override suspend fun fetchMe(): TwitchUserDetail? = dao.findMe()

    override suspend fun setMe(me: TwitchUserDetail) {
        dao.setMe(me, expiredAt = dateTimeProvider.now() + MAX_AGE_USER_DETAIL)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        return dao.findBroadcastersByFollowerId(userId, current = dateTimeProvider.now())
    }

    override suspend fun replaceAllFollowings(
        userId: TwitchUser.Id,
        followings: Collection<TwitchBroadcaster>,
    ) {
        dao.replaceAllBroadcasters(
            userId,
            followings,
            expiredAt = dateTimeProvider.now() + MAX_AGE_BROADCASTER,
        )
    }

    override suspend fun addFollowedStreams(followedStreams: Collection<TwitchStream>) {
        val me = fetchMe() ?: return
        dao.replaceAllStreams(
            me.id,
            followedStreams,
            expiredAt = dateTimeProvider.now() + MAX_AGE_STREAM,
        )
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): List<TwitchStream> {
        val id = me ?: fetchMe()?.id ?: return emptyList()
        val expiredAt = dao.findStreamExpire(id)?.expiredAt
        if (expiredAt?.isBefore(dateTimeProvider.now()) == true) {
            return emptyList()
        }
        return dao.findAllStreams()
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int
    ): List<TwitchChannelSchedule> {
        val schedule = dao.findChannelSchedule(id, current = dateTimeProvider.now())
        val current = dateTimeProvider.now()
        val finished = schedule.mapNotNull { it.segments }.flatten()
            .filter { it.endTime == null || current.isAfter(it.endTime) }
        if (finished.isEmpty()) {
            return schedule
        }
        dao.removeChannelStreamSchedulesByIds(finished.map { it.id })
        return dao.findChannelSchedule(id, current = dateTimeProvider.now())
    }

    override suspend fun setFollowedStreamSchedule(schedule: Collection<TwitchChannelSchedule>) {
        dao.replaceChannelSchedules(
            schedule,
            expiredAt = dateTimeProvider.now() + MAX_AGE_CHANNEL_SCHEDULE,
        )
    }

    override suspend fun fetchStreamDetail(
        id: TwitchVideo.TwitchVideoId,
    ): TwitchVideo<out TwitchVideo.TwitchVideoId>? {
        return when (id) {
            is TwitchStream.Id -> dao.findStream(id)
            is TwitchChannelSchedule.Stream.Id -> dao.findStreamSchedule(id)
            else -> throw AssertionError("unsupported id type: $id")
        }
    }

    override suspend fun getAuthorizeUrl(state: String): String = throw AssertionError()

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = emptyList()

    override suspend fun deleteAllTables() {
        database.withTransaction {
            database.query("PRAGMA defer_foreign_keys = TRUE", null)
            deleteDao.forEach { it.deleteTable() }
        }
    }

    companion object {
        private val MAX_AGE_BROADCASTER = Duration.ofHours(12)
        private val MAX_AGE_USER_DETAIL = Duration.ofDays(1)
        private val MAX_AGE_STREAM = Duration.ofMinutes(10)
        private val MAX_AGE_CHANNEL_SCHEDULE = Duration.ofDays(1)
    }
}
