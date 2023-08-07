package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.remote.TwitchLiveRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchLiveRepository @Inject constructor(
    private val remoteDataStore: TwitchLiveRemoteDataSource,
) {
    suspend fun getAuthorizeUrl(): String = remoteDataStore.getAuthorizeUrl()

    private val users = mutableMapOf<LiveChannel.Id, LiveChannelDetail>()
    suspend fun findUsersById(
        ids: Collection<LiveChannel.Id>? = null,
    ): List<LiveChannelDetail> {
        if (ids == null) {
            val me = checkNotNull(fetchMe())
            return listOf(me)
        }
        val cache = ids.mapNotNull { users[it] }
        if (cache.size == ids.size) {
            return cache
        }
        val remoteIds = ids - cache.map { it.id }.toSet()
        return remoteDataStore.findUsersById(remoteIds)
    }

    private var me: LiveChannelDetail? = null
    suspend fun fetchMe(): LiveChannelDetail? {
        if (me != null) {
            return me
        }
        val res = remoteDataStore.findUsersById().firstOrNull()
        this.me = res
        return res
    }

    private val followings = mutableListOf<LiveSubscription>()
    suspend fun fetchAllFollowings(
        userId: LiveChannel.Id,
    ): List<LiveSubscription> {
        if (followings.isNotEmpty()) {
            return followings
        }
        return remoteDataStore.fetchAllFollowings(userId)
    }

    private val _onAir = MutableStateFlow<List<LiveVideo>>(emptyList())
    val onAir: Flow<List<LiveVideo>> = _onAir
    suspend fun fetchFollowedStreams(): List<LiveVideo> {
        val me = fetchMe() ?: return emptyList()
        val res = remoteDataStore.fetchFollowedStreams(me)
        _onAir.value = res
        return res
    }

    private val _upcoming = MutableStateFlow<Map<LiveChannel.Id, List<LiveVideo>>>(emptyMap())
    val upcoming: Flow<List<LiveVideo>> = _upcoming.map { it.values.flatten() }
    suspend fun fetchFollowedStreamSchedule(
        id: LiveChannel.Id,
        maxCount: Int = 10,
    ): List<LiveVideo> {
        val res = remoteDataStore.fetchFollowedStreamSchedule(id, maxCount)
        _upcoming.update {
            it.toMutableMap().apply { this[id] = res }
        }
        return res
    }

    fun fetchStreamDetail(id: LiveVideo.Id): LiveVideo {
        check(id.platform == LivePlatform.TWITCH)
        return _onAir.value.firstOrNull { it.id == id } ?: _upcoming.value.values.flatten()
            .first { it.id == id }
    }

    suspend fun fetchVideosByChannelId(id: LiveChannel.Id, itemCount: Int = 20): List<LiveVideo> {
        return remoteDataStore.fetchVideosByChannelId(id, itemCount)
    }

    companion object {
        @Suppress("unused")
        private val TAG = TwitchLiveRepository::class.simpleName
    }
}

data class TwitchOauthToken(
    val accessToken: String,
    val scope: String,
    val state: String,
    val tokenType: String,
) {
    companion object
}
