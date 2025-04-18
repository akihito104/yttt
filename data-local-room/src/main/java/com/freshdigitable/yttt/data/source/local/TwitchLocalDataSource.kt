package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveStream
import com.freshdigitable.yttt.data.model.TwitchLiveVideo
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.model.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val dateTimeProvider: DateTimeProvider,
    imageDataSource: ImageDataSource,
) : TwitchDataSource.Local, ImageDataSource by imageDataSource {
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

    override suspend fun removeStreamScheduleById(id: Set<TwitchChannelSchedule.Stream.Id>) {
        dao.removeChannelStreamSchedulesByIds(id)
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): TwitchStreams? {
        val id = me ?: fetchMe()?.id ?: return null
        return dao.findStreamByMe(id)
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int, // ignore
    ): TwitchChannelSchedule? {
        val expire = dao.findChannelScheduleExpire(id)
        val current = dateTimeProvider.now()
        if (expire != null && expire.expiredAt <= current) {
            return null
        }
        return dao.findChannelSchedule(id)
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): List<TwitchCategory> =
        dao.fetchCategory(id)

    override suspend fun upsertCategory(category: Collection<TwitchCategory>) {
        dao.upsertCategory(category)
    }

    override suspend fun setFollowedStreamSchedule(
        userId: TwitchUser.Id,
        schedule: TwitchChannelSchedule?
    ) {
        val updatableAt = dateTimeProvider.now() + MAX_AGE_CHANNEL_SCHEDULE
        if (schedule == null) {
            dao.updateChannelScheduleExpireEntity(userId, updatableAt)
        } else {
            dao.replaceChannelSchedules(schedule, expiredAt = updatableAt)
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

    override val onAir: Flow<List<TwitchLiveStream>> = dao.watchStream()
    override val upcoming: Flow<List<TwitchLiveSchedule>> = dao.watchLiveSchedule()

    override suspend fun fetchStreamDetail(
        id: TwitchVideo.TwitchVideoId,
    ): TwitchLiveVideo<out TwitchVideo.TwitchVideoId>? = when (id) {
        is TwitchStream.Id -> dao.findStream(id)
        is TwitchChannelSchedule.Stream.Id -> dao.findStreamSchedule(id)
        else -> throw AssertionError("unsupported id type: $id")
    }

    companion object {
        private val MAX_AGE_USER_DETAIL = Duration.ofDays(1)
        private val MAX_AGE_CHANNEL_SCHEDULE = Duration.ofDays(1)
    }
}
