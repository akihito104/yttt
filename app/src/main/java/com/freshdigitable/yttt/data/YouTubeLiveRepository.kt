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

    private val videoRemote = mutableMapOf<LiveVideo.Id, LiveVideoDetail>()
    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val res = remoteSource.fetchVideoList(ids)
        localSource.addVideo(res)
        videoRemote.putAll(res.map { it.id to it })
        return res
    }

    suspend fun fetchVideoListByPlaylistId(
        id: LivePlaylist.Id,
        maxResult: Long = 10,
    ): List<LiveVideo> {
        val playlistItems = remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
        val uploadedAtAnotherChannel = playlistItems
            .filter { it.channel.id != it.videoOwnerChannelId }
            .mapNotNull { it.videoOwnerChannelId }
        if (uploadedAtAnotherChannel.isNotEmpty()) {
            fetchChannelList(uploadedAtAnotherChannel)
        }

        val videoIds = playlistItems.map { it.videoId }.toSet()
        val cachedVideo = localSource.fetchVideoList(videoIds)
        val remoteVideoIds = videoIds - cachedVideo.map { it.id }.toSet()
        val remoteVideo = fetchVideoList(remoteVideoIds)
        return cachedVideo + remoteVideo
    }

    suspend fun fetchVideoDetail(id: LiveVideo.Id): LiveVideo {
        return videoRemote[id] ?: fetchVideoList(listOf(id)).first()
    }

    suspend fun removeAllFinishedVideos() {
        localSource.removeAllFinishedVideos()
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
        if (cache.size == ids.size) {
            return cache
        }
        val needed = ids - cache.map { it.id }.toSet()
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

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
