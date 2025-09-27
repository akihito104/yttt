package com.freshdigitable.yttt.data.source.local

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
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.TwitchScheduleDataSource
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import com.freshdigitable.yttt.data.source.TwitchUserDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TwitchLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val ioScope: IoScope,
    userDataSource: TwitchUserLocalDataSource,
    streamDataSource: TwitchStreamLocalDataSource,
    scheduleDataSource: TwitchScheduleLocalDataSource,
) : TwitchDataSource.Local,
    TwitchUserDataSource.Local by userDataSource,
    TwitchStreamDataSource.Local by streamDataSource,
    TwitchScheduleDataSource.Local by scheduleDataSource,
    ImageDataSource by streamDataSource {
    override suspend fun fetchCategory(id: Set<TwitchCategory.Id>): Result<List<TwitchCategory>> =
        ioScope.asResult { dao.fetchCategory(id) }

    override suspend fun upsertCategory(category: Collection<TwitchCategory>) {
        dao.upsertCategoryEntities(category)
    }

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): Result<List<Updatable<TwitchVideoDetail>>> = Result.success(emptyList())
}

@Singleton
internal class TwitchExtendedDataSource @Inject constructor(
    private val dao: TwitchDao,
    userDataSource: TwitchUserLocalDataSource,
    streamDataSource: TwitchStreamLocalDataSource,
    scheduleDataSource: TwitchScheduleLocalDataSource,
) : TwitchDataSource.Extended,
    TwitchUserDataSource.Extended by userDataSource,
    TwitchStreamDataSource.Extended by streamDataSource,
    TwitchScheduleDataSource.Extended by scheduleDataSource {
    override suspend fun cleanUpByUserId(ids: Collection<TwitchUser.Id>) {
        dao.cleanUpByUserId(ids)
    }

    override suspend fun deleteAllTables() {
        dao.deleteTable()
    }
}

@Singleton
internal class TwitchUserLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val ioScope: IoScope,
) : TwitchUserDataSource.Local, TwitchUserDataSource.Extended {
    override suspend fun findUsersById(ids: Set<TwitchUser.Id>?): Result<List<Updatable<TwitchUserDetail>>> {
        if (ids == null) {
            return fetchMe().map { listOfNotNull(it) }
        }
        return ioScope.asResult { dao.findUserDetail(ids) }
    }

    override suspend fun addUsers(users: Collection<Updatable<TwitchUserDetail>>) {
        dao.addUserDetailEntities(users)
    }

    override suspend fun fetchMe(): Result<Updatable<TwitchUserDetail>?> = ioScope.asResult { dao.findMe() }

    override suspend fun setMe(me: Updatable<TwitchUserDetail>) {
        dao.setMeEntities(me)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): Result<Updatable<TwitchFollowings>> =
        ioScope.asResult { dao.findFollowingsByFollowerId(userId) }

    override suspend fun replaceAllFollowings(followings: Updatable<TwitchFollowings>) {
        dao.replaceAllBroadcasterEntities(followings)
    }
}

@Singleton
internal class TwitchStreamLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val ioScope: IoScope,
    imageDataSource: ImageDataSource,
) : TwitchStreamDataSource.Local, TwitchStreamDataSource.Extended, ImageDataSource by imageDataSource {
    override val onAir: Flow<List<TwitchLiveStream>> = dao.watchStream()

    override suspend fun replaceFollowedStreams(followedStreams: Updatable<TwitchStreams.Updated>) {
        dao.replaceAllStreams(followedStreams)
        removeImageByUrl(followedStreams.item.deletedThumbnails)
    }

    override suspend fun fetchStreamDetail(
        id: TwitchVideo.TwitchVideoId,
    ): TwitchLiveVideo<out TwitchVideo.TwitchVideoId>? = when (id) {
        is TwitchStream.Id -> dao.findStream(id)
        is TwitchChannelSchedule.Stream.Id -> dao.findStreamSchedule(id)
        else -> throw AssertionError("unsupported id type: $id")
    }

    override suspend fun fetchFollowedStreams(me: TwitchUser.Id?): Result<Updatable<TwitchStreams>?> {
        val id = me ?: dao.findMe()?.item?.id ?: return Result.success(null)
        return ioScope.asResult { dao.findStreamByMe(id) }
    }
}

@Singleton
internal class TwitchScheduleLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
    private val ioScope: IoScope,
) : TwitchScheduleDataSource.Local, TwitchScheduleDataSource.Extended {
    override val upcoming: Flow<List<TwitchLiveSchedule>> = dao.watchLiveSchedule()

    override suspend fun removeStreamScheduleById(id: Set<TwitchChannelSchedule.Stream.Id>) {
        dao.removeChannelStreamSchedulesByIds(id)
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int, // ignore
    ): Result<Updatable<TwitchChannelSchedule?>> = ioScope.asResult {
        dao.findChannelSchedule(id)
    }

    override suspend fun setFollowedStreamSchedule(
        userId: TwitchUser.Id,
        schedule: Updatable<TwitchChannelSchedule?>,
    ) {
        dao.replaceChannelScheduleEntities(userId, schedule)
    }
}
