package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLiveLocalDataSource
import com.freshdigitable.yttt.data.source.remote.YouTubeLiveRemoteDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveRepository @Inject constructor(
    private val remoteSource: YouTubeLiveRemoteDataSource,
    private val localSource: YouTubeLiveLocalDataSource,
) : YoutubeLiveDataSource {
    val subscriptions: Flow<List<LiveSubscription>> = localSource.subscriptions
    val videos: Flow<List<LiveVideo>> = localSource.videos

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
        publishedAfter: Instant,
        maxResult: Long
    ): List<LiveChannelLog> {
        val logs = localSource.fetchLiveChannelLogs(channelId)
        val latestLog = if (logs.isNotEmpty()) logs.maxOf { it.dateTime } else publishedAfter
        val res = remoteSource.fetchLiveChannelLogs(channelId, latestLog, maxResult)
        localSource.addLiveChannelLogs(res)
        return res
    }

    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val res = remoteSource.fetchVideoList(ids)
        localSource.addVideo(res)
        return res
    }

    suspend fun findAllUnfinishedVideos(): List<LiveVideo> {
        return localSource.findAllUnfinishedVideos()
    }

    suspend fun deleteVideo(removed: Collection<LiveVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        localSource.deleteVideo(removed)
    }

    suspend fun fetchChannelList(ids: Collection<LiveChannel.Id>): List<LiveChannel> {
        return remoteSource.fetchChannelList(ids)
    }

    suspend fun fetchChannelSection(id: LiveChannel.Id): List<LiveChannelSection> {
        return remoteSource.fetchChannelSection(id)
    }
}
