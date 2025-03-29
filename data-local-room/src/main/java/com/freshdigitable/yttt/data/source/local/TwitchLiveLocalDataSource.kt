package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLiveLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val dateTimeProvider: DateTimeProvider,
    imageDataSource: ImageDataSource,
) : TwitchLiveDataSource.Local, ImageDataSource by imageDataSource {
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

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): TwitchFollowings =
        dao.findFollowingsByFollowerId(userId)

    override suspend fun replaceAllFollowings(followings: TwitchFollowings) {
        dao.replaceAllBroadcasters(followings)
    }

    override suspend fun replaceFollowedStreams(followedStreams: TwitchStreams.Updated) {
        dao.replaceAllStreams(followedStreams)
        removeImageByUrl(followedStreams.deletedThumbnails)
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): TwitchStreams? {
        val id = me ?: fetchMe()?.id ?: return null
        return dao.findStreamByMe(id)
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

    override suspend fun setFollowedStreamSchedule(
        userId: TwitchUser.Id,
        schedule: Collection<TwitchChannelSchedule>,
    ) {
        val expiredAt = dateTimeProvider.now() + MAX_AGE_CHANNEL_SCHEDULE
        if (schedule.isEmpty()) {
            dao.updateChannelScheduleExpireEntity(userId, expiredAt)
        } else {
            dao.replaceChannelSchedules(schedule, expiredAt = expiredAt)
        }
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

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = emptyList()

    override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>) {
        dao.cleanUpByUserId(ids)
    }

    override suspend fun deleteAllTables() {
        dao.deleteTable()
    }

    companion object {
        private val MAX_AGE_USER_DETAIL = Duration.ofDays(1)
        private val MAX_AGE_CHANNEL_SCHEDULE = Duration.ofDays(1)
    }
}
