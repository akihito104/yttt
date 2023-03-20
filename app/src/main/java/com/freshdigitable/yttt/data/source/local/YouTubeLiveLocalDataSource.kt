package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLiveLocalDataSource @Inject constructor(
    private val database: AppDatabase,
) : YoutubeLiveDataSource {
    val videos: Flow<List<LiveVideo>> = database.dao.findAllUnfinishedVideos()

    override suspend fun fetchAllSubscribe(maxResult: Long): List<LiveSubscription> =
        withContext(Dispatchers.IO) {
            database.dao.findAllSubscriptions()
        }

    suspend fun addSubscribes(subscriptions: Collection<LiveSubscription>) =
        withContext(Dispatchers.IO) {
            val channels = subscriptions.map { it.channelId }.toSet()
                .filter { database.dao.findChannel(it) == null }
                .map { LiveChannelTable(id = it) }
            database.withTransaction {
                database.dao.addChannels(channels)
                database.dao.addSubscriptions(subscriptions.map { it.toDbEntity() })
            }
        }

    suspend fun removeSubscribes(subscriptions: Collection<LiveSubscription.Id>) {
        database.dao.removeSubscriptions(subscriptions)
    }

    override suspend fun fetchLiveChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant,
        maxResult: Long
    ): List<LiveChannelLog> {
        return database.dao.findChannelLogs(channelId, publishedAfter)
    }

    suspend fun addLiveChannelLogs(channelLogs: List<LiveChannelLog>) {
        val channels = channelLogs.map { it.channelId }.distinct()
            .filter { database.dao.findChannel(it) == null }
            .map { LiveChannelTable(id = it) }
        val videos = channelLogs.distinctBy { it.videoId }
            .filter { database.dao.findVideosById(listOf(it.videoId)).isEmpty() }
            .map { LiveVideoTable(id = it.videoId, channelId = it.channelId) }
        database.withTransaction {
            database.dao.addChannels(channels)
            database.dao.addVideos(videos)
            database.dao.addChannelLogs(channelLogs.map { it.toDbEntity() })
        }
    }

    override suspend fun fetchVideoList(ids: Collection<LiveVideo.Id>): List<LiveVideo> {
        return database.dao.findVideosById(ids)
    }

    suspend fun addVideo(video: Collection<LiveVideo>) = withContext(Dispatchers.IO) {
        val channels = video.map { it.channel }
            .groupBy { it.id }.values.flatten()
            .map { it.toDbEntity() }
        val videos = video.map { it.toDbEntity() }
        database.withTransaction {
            database.dao.addChannels(channels)
            database.dao.addVideos(videos)
        }
    }
}

private fun LiveSubscription.toDbEntity(): LiveSubscriptionTable = LiveSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channelId,
)

private fun LiveChannel.toDbEntity(): LiveChannelTable = LiveChannelTable(
    id = id, title = title,
)

private fun LiveVideo.toDbEntity(): LiveVideoTable = LiveVideoTable(
    id = id,
    title = title,
    channelId = channel.id,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
)

private fun LiveChannelLog.toDbEntity(): LiveChannelLogTable = LiveChannelLogTable(
    id = id,
    dateTime = dateTime,
    videoId = videoId,
    channelId = channelId,
)
