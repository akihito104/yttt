package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
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
    private val users = mutableMapOf<LiveChannel.Id, LiveChannelDetail>()
    private var me: LiveChannelDetail? = null

    override suspend fun findUsersById(
        ids: Collection<LiveChannel.Id>?,
    ): List<LiveChannelDetail> {
        if (ids == null) {
            return listOfNotNull(me)
        }
        return ids.mapNotNull { users[it] }
    }

    suspend fun addUsers(users: Collection<LiveChannelDetail>) {
        users.forEach {
            this.users[it.id] = it
        }
    }

    override suspend fun fetchMe(): LiveChannelDetail? = me

    suspend fun setMe(me: LiveChannelDetail?) {
        this.me = me
    }

    private val followings = mutableMapOf<LiveChannel.Id, CachedData<List<LiveSubscription>>>()
    override suspend fun fetchAllFollowings(
        userId: LiveChannel.Id,
    ): List<LiveSubscription> {
        val deadline = followings[userId]?.deadline ?: return emptyList()
        if (deadline.isAfter(Instant.now())) {
            return emptyList()
        }
        return followings[userId]?.data ?: emptyList()
    }

    suspend fun addFollowings(userId: LiveChannel.Id, followings: List<LiveSubscription>) {
        val data = CachedData(followings, Instant.now() + CACHE_ALIVE_TIME)
        this.followings[userId] = data
    }

    fun addFollowedStreams(followedStreams: List<LiveVideo>) {
        _onAir.value = followedStreams
    }

    private val _onAir = MutableStateFlow<List<LiveVideo>>(emptyList())
    override val onAir: Flow<List<LiveVideo>> = _onAir
    override suspend fun fetchFollowedStreams(): List<LiveVideo> = _onAir.value

    private val _upcoming = MutableStateFlow<Map<LiveChannel.Id, List<LiveVideo>>>(emptyMap())
    override val upcoming: Flow<List<LiveVideo>> = _upcoming.map { it.values.flatten() }

    override suspend fun fetchFollowedStreamSchedule(
        id: LiveChannel.Id,
        maxCount: Int, // ignored
    ): List<LiveVideo> {
        return _upcoming.value[id] ?: emptyList()
    }

    suspend fun updateFollowedStreamSchedule(id: LiveChannel.Id, schedule: List<LiveVideo>) {
        _upcoming.update {
            it.toMutableMap().apply { this[id] = schedule }
        }
    }

    override suspend fun fetchStreamDetail(id: LiveVideo.Id): LiveVideo {
        return _onAir.value.firstOrNull { it.id == id }
            ?: _upcoming.value.values.flatten().first { it.id == id }
    }

    override suspend fun getAuthorizeUrl(): String = throw AssertionError()

    override suspend fun fetchVideosByChannelId(
        id: LiveChannel.Id,
        itemCount: Int
    ): List<LiveVideo> = throw AssertionError()

    companion object {
        private val CACHE_ALIVE_TIME = Duration.ofHours(12)
    }
}

private data class CachedData<E>(
    val data: E,
    val deadline: Instant,
)
