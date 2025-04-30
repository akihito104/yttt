package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable
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
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val dateTimeProvider: DateTimeProvider,
    private val ioScope: IoScope,
    imageDataSource: ImageDataSource,
) : TwitchDataSource.Local, ImageDataSource by imageDataSource {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<TwitchUserDetail>> {
        if (ids == null) {
            return fetchMe().map { listOfNotNull(it) }
        }
        return ioScope.asResult {
            dao.findUserDetail(ids, current = dateTimeProvider.now())
        }
    }

    override suspend fun addUsers(users: Collection<TwitchUserDetail>) {
        dao.addUserDetails(users, expiredAt = dateTimeProvider.now() + MAX_AGE_USER_DETAIL)
    }

    override suspend fun fetchMe(): Result<TwitchUserDetail?> = ioScope.asResult { dao.findMe() }

    override suspend fun setMe(me: TwitchUserDetail) {
        dao.setMe(me, expiredAt = dateTimeProvider.now() + MAX_AGE_USER_DETAIL)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<TwitchFollowings> =
        ioScope.asResult { dao.findFollowingsByFollowerId(userId) }

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

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<TwitchStreams?> {
        val id = me ?: fetchMe().getOrNull()?.id ?: return Result.success(null)
        return ioScope.asResult { dao.findStreamByMe(id) }
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int, // ignore
    ): Result<TwitchChannelScheduleUpdatable> = ioScope.asResult {
        val expire = dao.findChannelScheduleExpire(id)
        val schedule = dao.findChannelSchedule(id)
        object : TwitchChannelScheduleUpdatable {
            override val schedule: TwitchChannelSchedule? get() = schedule
            override val updatableAt: Instant get() = expire?.expiredAt ?: Instant.EPOCH
        }
    }

    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        ioScope.asResult { dao.fetchCategory(id) }

    override suspend fun upsertCategory(category: Collection<TwitchCategory>) {
        dao.upsertCategory(category)
    }

    override suspend fun setFollowedStreamSchedule(
        userId: TwitchUser.Id,
        schedule: TwitchChannelScheduleUpdatable,
    ) {
        dao.replaceChannelSchedules(userId, schedule)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<TwitchVideoDetail>> = Result.success(emptyList())

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
    }
}
