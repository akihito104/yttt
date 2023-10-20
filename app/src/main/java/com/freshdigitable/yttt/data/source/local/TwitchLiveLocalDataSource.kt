package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.source.TwitchBroadcaster
import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchUserDetail
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.TwitchVideoDetail
import com.freshdigitable.yttt.data.source.toTwitchVideoList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveLocalDataSource @Inject constructor() : TwitchLiveDataSource {
    private val users = mutableMapOf<TwitchUser.Id, TwitchUserDetail>()
    private var me: TwitchUserDetail? = null

    override suspend fun findUsersById(ids: Collection<TwitchUser.Id>?): List<TwitchUserDetail> {
        if (ids == null) {
            return listOfNotNull(me)
        }
        return ids.mapNotNull { users[it] }
    }

    suspend fun addUsers(users: Collection<TwitchUserDetail>) {
        users.forEach {
            this.users[it.id] = it
        }
    }

    override suspend fun fetchMe(): TwitchUser? = me

    suspend fun setMe(me: TwitchUserDetail?) {
        this.me = me
    }

    private val followings = mutableMapOf<TwitchUser.Id, CachedData<List<TwitchBroadcaster>>>()
    override suspend fun fetchAllFollowings(userId: TwitchUser.Id): List<TwitchBroadcaster> {
        val deadline = followings[userId]?.deadline ?: return emptyList()
        if (deadline.isAfter(Instant.now())) {
            return emptyList()
        }
        return followings[userId]?.data ?: emptyList()
    }

    suspend fun addFollowings(userId: TwitchUser.Id, followings: List<TwitchBroadcaster>) {
        val data = CachedData(followings, Instant.now() + CACHE_ALIVE_TIME)
        this.followings[userId] = data
    }

    fun addFollowedStreams(followedStreams: List<TwitchStream>) {
        _onAir.value = followedStreams
    }

    private val _onAir = MutableStateFlow<List<TwitchStream>>(emptyList())
    override val onAir: Flow<List<TwitchStream>> = _onAir
    override suspend fun fetchFollowedStreams(): List<TwitchStream> = _onAir.value

    private val _upcoming =
        MutableStateFlow<Map<TwitchUser.Id, List<TwitchChannelSchedule>>>(emptyMap())
    override val upcoming: Flow<List<TwitchChannelSchedule>> = _upcoming.map { it.values.flatten() }

    override suspend fun fetchFollowedStreamSchedule(
        id: TwitchUser.Id,
        maxCount: Int
    ): List<TwitchChannelSchedule> {
        return _upcoming.value[id] ?: emptyList()
    }

    suspend fun updateFollowedStreamSchedule(
        id: TwitchUser.Id,
        schedule: List<TwitchChannelSchedule>
    ) {
        _upcoming.update {
            it.toMutableMap().apply { this[id] = schedule }
        }
    }

    override suspend fun fetchStreamDetail(
        id: TwitchVideo.TwitchVideoId,
    ): TwitchVideo<out TwitchVideo.TwitchVideoId>? {
        return when (id) {
            is TwitchStream.Id -> {
                _onAir.value.firstOrNull { it.id == id }
            }

            is TwitchChannelSchedule.Stream.Id -> {
                _upcoming.value.values.flatten()
                    .map { it.toTwitchVideoList() }.flatten()
                    .firstOrNull { it.id == id }
            }

            else -> throw AssertionError("unsupported id type: $id")
        }
    }

    override suspend fun getAuthorizeUrl(): String = throw AssertionError()

    override suspend fun fetchVideosByUserId(
        id: TwitchUser.Id,
        itemCount: Int
    ): List<TwitchVideoDetail> = throw AssertionError()

    companion object {
        private val CACHE_ALIVE_TIME = Duration.ofHours(12)
    }
}

private data class CachedData<E>(
    val data: E,
    val deadline: Instant,
)
