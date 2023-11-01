package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.TwitchDao
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.TwitchVideoDetail
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
) : TwitchLiveDataSource {
    override val onAir: Flow<List<TwitchStream>> = dao.watchStream()
    override val upcoming: Flow<List<TwitchChannelSchedule>> = dao.watchChannelSchedule()

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            return listOfNotNull(fetchMe())
        }
        return dao.findUserDetail(ids)
    }

    suspend fun addUsers(users: Collection<TwitchUserDetail>) {
        dao.addUserDetails(users)
    }

    override suspend fun fetchMe(): TwitchUserDetail? = dao.findMe()

    suspend fun setMe(me: TwitchUserDetail) {
        dao.setMe(me)
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        return dao.findBroadcastersByFollowerId(userId)
    }

    suspend fun replaceAllFollowings(userId: TwitchUser.Id, followings: List<TwitchBroadcaster>) {
        dao.replaceAllBroadcasters(userId, followings)
    }

    suspend fun addFollowedStreams(followedStreams: List<TwitchStream>) {
        val me = fetchMe() ?: return
        dao.replaceAllStreams(me.id, followedStreams)
    }

    override suspend fun fetchFollowedStreams(): List<TwitchStream> {
        val me = fetchMe() ?: return emptyList()
        val expiredAt = dao.findStreamExpire(me.id)?.expiredAt
        if (expiredAt?.isBefore(Instant.now()) == true) {
            return emptyList()
        }
        return dao.findAllStreams()
    }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int
    ): List<TwitchChannelSchedule> {
        val schedule = dao.findChannelSchedule(id)
        val current = Instant.now()
        val finished = schedule.mapNotNull { it.segments }.flatten()
            .filter { current.isAfter(it.endTime) }
        if (finished.isEmpty()) {
            return schedule
        }
        dao.removeChannelStreamSchedulesByIds(finished.map { it.id })
        return dao.findChannelSchedule(id)
    }

    suspend fun setFollowedStreamSchedule(schedule: List<TwitchChannelSchedule>) {
        dao.replaceChannelSchedules(schedule)
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

    override suspend fun getAuthorizeUrl(): String = throw AssertionError()

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int,
    ): List<TwitchVideoDetail> = emptyList()
}
