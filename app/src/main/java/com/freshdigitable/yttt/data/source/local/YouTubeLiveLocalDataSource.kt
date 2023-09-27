package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveChannelSection
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.LiveVideoDetail
import com.freshdigitable.yttt.data.source.YoutubeLiveDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
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

    override suspend fun addFreeChatItems(ids: Collection<LiveVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = true) }
        database.dao.addFreeChatItems(f)
    }

    override suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>) {
        val f = ids.map { FreeChatTable(it, isFreeChat = false) }
        database.dao.addFreeChatItems(f)
    }

    suspend fun addVideo(video: Collection<LiveVideo>) = withContext(Dispatchers.IO) {
        val defaultExpired = Instant.now() + Duration.ofMinutes(10)
        val expiring = video.map {
            val expired = when {
                it.isFreeChat == true -> Instant.now() + Duration.ofDays(1)
                it.isUpcoming() -> it.scheduledStartDateTime ?: defaultExpired
                it.isNowOnAir() -> Instant.now()
                !it.isLiveStream() || it.actualEndDateTime != null -> Instant.ofEpochMilli(Long.MAX_VALUE) // for DB limitation :(
                else -> defaultExpired
            }
            LiveVideoExpireTable(it.id, expired)
        }
        val videos = video.map { it.toDbEntity() }
        database.withTransaction {
            database.dao.addVideos(videos)
            database.dao.addLiveVideoExpire(expiring)
        }
    }

    private val videoDetailCache = mutableMapOf<LiveVideo.Id, LiveVideoDetail>()
    suspend fun fetchVideoDetail(id: LiveVideo.Id): LiveVideoDetail? {
        return videoDetailCache[id]
    }

    suspend fun addVideoDetail(detail: Collection<LiveVideoDetail>) {
        if (detail.isEmpty()) {
            return
        }
        videoDetailCache.putAll(detail.map { it.id to it })
    }

    suspend fun removeVideoDetail(id: LiveVideo.Id) {
        videoDetailCache.remove(id)
    }

    suspend fun removeAllFinishedVideos() {
        database.withTransaction {
            val dao = database.dao
            val v = dao.findAllFinishedVideos()
            dao.removeFreeChatItems(v.map { it.id })
            dao.removeAllChannelLogs()
            dao.removeAllFinishedVideos()
        }
    }

    suspend fun findAllUnfinishedVideos(): List<LiveVideo> {
        return database.dao.findAllUnfinishedVideoList()
    }

    suspend fun updateVideosInvisible(removed: Collection<LiveVideo.Id>) {
        if (removed.isEmpty()) {
            return
        }
        database.dao.updateVideoInvisible(removed)
    }

    suspend fun fetchChannelList(ids: Collection<LiveChannel.Id>): List<LiveChannelDetail> {
        return database.dao.findChannelDetail(ids)
    }

    suspend fun addChannelList(channelDetail: Collection<LiveChannelDetail>) {
        val channels = channelDetail.map { it.toDbEntity() }
        val additions = channelDetail.map { it.toAddition() }
        database.withTransaction {
            database.dao.addChannels(channels)
            database.dao.addChannelAddition(additions)
        }
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

private fun LiveChannelDetail.toAddition(): LiveChannelAdditionTable = LiveChannelAdditionTable(
    id = id,
    bannerUrl = bannerUrl,
    uploadedPlayList = uploadedPlayList,
    description = description,
    customUrl = customUrl,
    isSubscriberHidden = isSubscriberHidden,
    keywordsRaw = keywords.joinToString(","),
    publishedAt = publishedAt,
    subscriberCount = subscriberCount,
    videoCount = videoCount,
    viewsCount = viewsCount,
)
