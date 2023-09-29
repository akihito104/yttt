package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLiveLocalDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeLiveRemoteDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveRepository @Inject constructor(
    private val remoteSource: YouTubeLiveRemoteDataSource,
    private val localSource: YouTubeLiveLocalDataSource,
) : YoutubeLiveDataSource {
    val subscriptions: Flow<List<LiveSubscription>> = localSource.subscriptions
    val videos: Flow<List<LiveVideo>> = localSource.videos
    var lastUpdateDatetime: Instant? = null

    override suspend fun fetchAllSubscribe(maxResult: Long): List<LiveSubscription> {
        val res = remoteSource.fetchAllSubscribe(maxResult)
        val current = localSource.fetchAllSubscribe()
        val deleted = res.map { it.id }.toSet() - current.map { it.id }.toSet()
        localSource.removeSubscribes(deleted)
        localSource.addSubscribes(res)
        return res
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<LiveChannelLog> {
        val pa = if (publishedAfter != null) {
            publishedAfter
        } else {
            val cache = localSource.fetchLiveChannelLogs(channelId, maxResult = 1).firstOrNull()
            cache?.dateTime?.plusSeconds(1) ?: Instant.now().minus(activityMaxPeriod)
        }
        val res = remoteSource.fetchLiveChannelLogs(channelId, pa, maxResult)
        localSource.addLiveChannelLogs(res)
        return res
    }

    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val cache = localSource.fetchVideoList(ids)
        val neededId = ids - cache.map { it.id }.toSet()
        if (neededId.isEmpty()) {
            return cache
        }
        val res = remoteSource.fetchVideoList(neededId)
        localSource.addVideo(res)
        localSource.addVideoDetail(res)
        return cache + res
    }

    suspend fun fetchVideoIdListByPlaylistId(
        id: LivePlaylist.Id,
        maxResult: Long = 10,
    ): List<LiveVideo.Id> {
        val playlistItems = remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
        val uploadedAtAnotherChannel = playlistItems
            .filter { it.channel.id != it.videoOwnerChannelId }
            .mapNotNull { it.videoOwnerChannelId }
        if (uploadedAtAnotherChannel.isNotEmpty()) {
            fetchChannelList(uploadedAtAnotherChannel)
        }
        return playlistItems.map { it.videoId }
    }

    suspend fun fetchVideoDetail(id: LiveVideo.Id): LiveVideo? {
        val detailCache = localSource.fetchVideoDetail(id)
        val cache = fetchVideoList(listOf(id)).firstOrNull()
        if (cache == null) {
            localSource.removeVideoDetail(id)
            return null
        }
        if (detailCache != null) {
            return object : LiveVideoDetail by detailCache {
                override val isFreeChat: Boolean?
                    get() = cache.isFreeChat

                override fun toString(): String = detailCache.toString()
            }
        }
        return cache
    }

    suspend fun cleanUp(usefulItemIds: Collection<LiveVideo.Id>) {
        localSource.cleanUp(usefulItemIds)
    }

    suspend fun findAllUnfinishedVideos(): List<LiveVideo> {
        return localSource.findAllUnfinishedVideos()
    }

    suspend fun updateVideosInvisible(removed: Collection<LiveVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        localSource.updateVideosInvisible(removed)
    }

    suspend fun fetchChannelList(ids: Collection<LiveChannel.Id>): List<LiveChannelDetail> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val cache = localSource.fetchChannelList(ids)
        val needed = ids - cache.map { it.id }.toSet()
        if (needed.isEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelList(needed)
        localSource.addChannelList(remote)
        return remote
    }

    suspend fun fetchChannelSection(id: LiveChannel.Id): List<LiveChannelSection> {
        val cache = localSource.fetchChannelSection(id)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelSection(id)
        localSource.addChannelSection(remote)
        return remote
    }

    suspend fun fetchPlaylist(ids: Collection<LivePlaylist.Id>): List<LivePlaylist> {
        return remoteSource.fetchPlaylist(ids)
    }

    suspend fun fetchPlaylistItems(
        id: LivePlaylist.Id,
        maxResult: Long = 20,
    ): List<LivePlaylistItem> {
        return remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
    }

    override suspend fun addFreeChatItems(ids: Collection<LiveVideo.Id>) {
        localSource.addFreeChatItems(ids)
    }

    override suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>) {
        localSource.removeFreeChatItems(ids)
    }

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
