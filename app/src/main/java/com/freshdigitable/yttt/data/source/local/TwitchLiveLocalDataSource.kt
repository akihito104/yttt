package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.TwitchDao
import com.freshdigitable.yttt.data.model.toTable
import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.TwitchVideoDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveLocalDataSource @Inject constructor(
    private val dao: TwitchDao,
) : TwitchLiveDataSource {
    override val onAir: Flow<List<TwitchStream>> = dao.watchStream()
    override val upcoming: Flow<List<TwitchChannelSchedule>> = dao.watchChannelSchedule()
    private var me: TwitchUserDetail? = null

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            return listOfNotNull(me)
        }
        return dao.findUserDetail(ids)
    }

    suspend fun addUsers(users: Collection<TwitchUserDetail>) {
        dao.addUserDetails(users)
    }

    override suspend fun fetchMe(): TwitchUser? = me

    suspend fun setMe(me: TwitchUserDetail?) {
        this.me = me
    }

    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        return dao.findBroadcastersByFollowerId(userId)
    }

    suspend fun addFollowings(userId: TwitchUser.Id, followings: List<TwitchBroadcaster>) {
        dao.setBroadcasters(userId, followings)
    }

    suspend fun addFollowedStreams(followedStreams: List<TwitchStream>) {
        dao.addStreams(followedStreams.map { it.toTable() })
    }

    override suspend fun fetchFollowedStreams(): List<TwitchStream> = dao.findAllStreams()

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int
    ): List<TwitchChannelSchedule> = dao.findChannelSchedule(id)

    suspend fun updateFollowedStreamSchedule(schedule: List<TwitchChannelSchedule>) {
        dao.addChannelSchedules(schedule)
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
