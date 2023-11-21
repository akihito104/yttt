package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeRemoteDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val remoteSource: YouTubeRemoteDataSource,
    private val localSource: YouTubeLocalDataSource,
) : YoutubeDataSource {
    val subscriptions: Flow<List<YouTubeSubscription>> = localSource.subscriptions
    val videos: Flow<List<YouTubeVideo>> = localSource.videos
    var lastUpdateDatetime: Instant? = null

    override suspend fun fetchAllSubscribe(maxResult: Long): List<YouTubeSubscription> {
        val res = remoteSource.fetchAllSubscribe(maxResult)
        val current = localSource.fetchAllSubscribe()
        val deleted = res.map { it.id }.toSet() - current.map { it.id }.toSet()
        localSource.removeSubscribes(deleted)
        localSource.addSubscribes(res)
        return res
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<YouTubeChannelLog> {
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

    override suspend fun fetchVideoList(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideo> {
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
        return cache + res
    }

    suspend fun fetchVideoIdListByPlaylistId(
        id: YouTubePlaylist.Id,
        maxResult: Long = 10,
    ): List<YouTubeVideo.Id> {
        val cache = localSource.fetchPlaylistItems(id)
        val playlistItems = if (cache == null) {
            val items = remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
            localSource.setPlaylistItemsByPlaylistId(id, items)
            items
        } else if (cache.isEmpty()) {
            return emptyList()
        } else {
            cache
        }
        val uploadedAtAnotherChannel = playlistItems
            .filter { it.channel.id != it.videoOwnerChannelId }
            .mapNotNull { it.videoOwnerChannelId }
        if (uploadedAtAnotherChannel.isNotEmpty()) {
            fetchChannelList(uploadedAtAnotherChannel)
        }
        return playlistItems.map { it.videoId }
    }

    suspend fun cleanUp() {
        localSource.cleanUp()
    }

    suspend fun findAllUnfinishedVideos(): List<YouTubeVideo> {
        return localSource.findAllUnfinishedVideos()
    }

    suspend fun removeVideo(removed: Collection<YouTubeVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        localSource.removeVideo(removed)
    }

    suspend fun fetchChannelList(ids: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetail> {
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

    suspend fun fetchChannelSection(id: YouTubeChannel.Id): List<YouTubeChannelSection> {
        val cache = localSource.fetchChannelSection(id)
        if (cache.isNotEmpty()) {
            return cache
        }
        val remote = remoteSource.fetchChannelSection(id)
        localSource.addChannelSection(remote)
        return remote
    }

    suspend fun fetchPlaylist(ids: Collection<YouTubePlaylist.Id>): List<YouTubePlaylist> {
        return remoteSource.fetchPlaylist(ids)
    }

    suspend fun fetchPlaylistItems(
        id: YouTubePlaylist.Id,
        maxResult: Long = 20,
    ): List<YouTubePlaylistItem> {
        return remoteSource.fetchPlaylistItems(id, maxResult = maxResult)
    }

    override suspend fun addFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        localSource.addFreeChatItems(ids)
    }

    override suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>) {
        localSource.removeFreeChatItems(ids)
    }

    companion object {
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
