package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelSection
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
    val subscriptions: Flow<List<LiveSubscription>> = database.dao.watchAllSubscriptions()
    val videos: Flow<List<LiveVideo>> = database.dao.watchAllUnfinishedVideos()

    override suspend fun fetchAllSubscribe(maxResult: Long): List<LiveSubscription> =
        withContext(Dispatchers.IO) {
            database.dao.findAllSubscriptions()
        }

    suspend fun addSubscribes(subscriptions: Collection<LiveSubscription>) =
        withContext(Dispatchers.IO) {
            val channels = subscriptions.map { it.channel }.toSet()
                .filter { database.dao.findChannel(it.id) == null }
                .map { it.toDbEntity() }
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
        publishedAfter: Instant?,
        maxResult: Long?,
    ): List<LiveChannelLog> {
        if (publishedAfter != null) {
            return database.dao.findChannelLogs(channelId, publishedAfter, maxResult)
        }
        return database.dao.findChannelLogs(channelId, maxResult)
    }

    suspend fun addLiveChannelLogs(channelLogs: List<LiveChannelLog>) {
        val channels = channelLogs.map { it.channelId }.distinct()
            .filter { database.dao.findChannel(it) == null }
            .map { LiveChannelTable(id = it) }
        val videos = channelLogs.distinctBy { it.videoId }
            .filter { database.dao.findVideosById(listOf(it.videoId)).isEmpty() }
            .map {
                LiveVideoTable(
                    id = it.videoId,
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
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
        val videos = video.map { it.toDbEntity() }
        database.withTransaction {
            database.dao.addVideos(videos)
        }
    }

    suspend fun findAllUnfinishedVideos(): List<LiveVideo> {
        return database.dao.findAllUnfinishedVideoList()
    }

    suspend fun deleteVideo(removed: Collection<LiveVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        database.dao.updateVideoInvisible(removed)
    }

    private val channelDetails = mutableMapOf<LiveChannel.Id, LiveChannelDetail>()
    fun fetchChannelList(ids: Collection<LiveChannel.Id>): List<LiveChannelDetail> {
        return ids.mapNotNull { channelDetails[it] }
    }

    fun addChannelList(channelDetail: Collection<LiveChannelDetail>) {
        channelDetail.forEach { channelDetails[it.id] = it }
    }

    private val channelSections = mutableMapOf<LiveChannel.Id, List<LiveChannelSection>>()
    fun fetchChannelSection(id: LiveChannel.Id): List<LiveChannelSection> {
        return channelSections[id] ?: emptyList()
    }

    fun addChannelSection(channelSection: List<LiveChannelSection>) {
        channelSections[channelSection[0].channelId] = channelSection
    }
}

private fun LiveSubscription.toDbEntity(): LiveSubscriptionTable = LiveSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id,
)

private fun LiveChannel.toDbEntity(): LiveChannelTable = LiveChannelTable(
    id = id, title = title, iconUrl = iconUrl,
)

private fun LiveVideo.toDbEntity(): LiveVideoTable = LiveVideoTable(
    id = id,
    title = title,
    channelId = channel.id,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    thumbnailUrl = thumbnailUrl,
)

private fun LiveChannelLog.toDbEntity(): LiveChannelLogTable = LiveChannelLogTable(
    id = id,
    dateTime = dateTime,
    videoId = videoId,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
)
