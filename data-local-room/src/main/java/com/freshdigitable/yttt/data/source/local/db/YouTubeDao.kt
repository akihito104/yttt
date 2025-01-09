package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtendedUpdatable
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

internal class YouTubeDao @Inject constructor(
    private val db: AppDatabase,
    private val videoDao: YouTubeVideoDaoImpl,
    private val playlistDao: YouTubePlaylistDaoImpl,
    private val subscriptionDao: YouTubeSubscriptionDaoImpl,
    private val channelDao: YouTubeChannelDaoImpl,
) : YouTubeVideoDao by videoDao, YouTubePlaylistDao by playlistDao,
    YouTubeSubscriptionDao by subscriptionDao, YouTubeChannelDao by channelDao {
    suspend fun addSubscriptions(
        subscriptions: Collection<YouTubeSubscription>
    ) = db.withTransaction {
        val channels = subscriptions.map { it.channel }.toSet()
            .map { it.toDbEntity() }
        addChannels(channels)
        addSubscriptionEntities(subscriptions.map { it.toDbEntity() })
    }

    suspend fun addChannelLogs(logs: Collection<YouTubeChannelLog>) = db.withTransaction {
        val channels = logs.map { it.channelId }.distinct()
            .filter { findChannel(it) == null }
            .map { YouTubeChannelTable(id = it) }
        val vIds = logs.map { it.videoId }.toSet()
        val found = findVideosById(vIds).map { it.id }.toSet()
        val videos = logs.distinctBy { it.videoId }
            .filter { !found.contains(it.videoId) }
            .map {
                YouTubeVideoTable(
                    id = it.videoId,
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        addChannels(channels)
        addVideoEntities(videos)
        addChannelLogEntities(logs.map { it.toDbEntity() })
    }

    suspend fun addVideos(videos: Collection<YouTubeVideoExtendedUpdatable>) = db.withTransaction {
        val v = videos.map { it.toDbEntity() }
        val freeChat = videos.map { FreeChatTable(it.id, it.isFreeChat) }
        val expiring = videos.map { YouTubeVideoExpireTable(it.id, it.updatableAt) }
        addVideoEntities(v)
        addFreeChatItemEntities(freeChat)
        addLiveVideoExpire(expiring)
    }

    suspend fun removeVideos(videoIds: Collection<YouTubeVideo.Id>) = db.withTransaction {
        removeFreeChatItems(videoIds)
        removeLiveVideoExpire(videoIds)
        removeVideoEntities(videoIds)
    }

    suspend fun addChannelDetails(
        channelDetail: Collection<YouTubeChannelDetail>,
        expiredAt: Instant,
    ) = db.withTransaction {
        val channels = channelDetail.map { it.toDbEntity() }
        val additions = channelDetail.map { it.toAddition() }
        val playlists = additions.mapNotNull { it.uploadedPlayList }
            .distinct()
            .map { YouTubePlaylistTable(it) }
        val expired = additions.map { YouTubeChannelAdditionExpireTable(it.id, expiredAt) }
        addChannels(channels)
        addPlaylists(playlists)
        addChannelAddition(additions)
        addChannelAdditionExpire(expired)
    }

    suspend fun addFreeChatItems(
        ids: Collection<YouTubeVideo.Id>,
        isFreeChat: Boolean,
        expiredAt: Instant,
    ) = db.withTransaction {
        val entities = ids.map { FreeChatTable(it, isFreeChat = isFreeChat) }
        val expires = ids.map { YouTubeVideoExpireTable(it, expiredAt) }
        addFreeChatItemEntities(entities)
        addLiveVideoExpire(expires)
    }

    suspend fun setPlaylistItems(
        id: YouTubePlaylist.Id,
        lastModified: Instant,
        maxAge: Duration? = null,
        items: Collection<YouTubePlaylistItem>,
    ) = db.withTransaction {
        if (items.isEmpty()) {
            addPlaylist(YouTubePlaylistTable.createWithMaxAge(id, lastModified))
        } else if (maxAge == null) {
            addPlaylist(YouTubePlaylistTable(id, lastModified))
        } else {
            updatePlaylist(id, maxAge = maxAge, lastModified = lastModified)
        }
        removePlaylistItemsByPlaylistId(id)
        if (items.isNotEmpty()) {
            addPlaylistItems(items.map { it.toDbEntity() })
        }
    }

    override suspend fun deleteTable() = db.withTransaction {
        db.deferForeignKeys()
        listOf(videoDao, channelDao, subscriptionDao, playlistDao).forEach { it.deleteTable() }
    }
}

private fun YouTubeSubscription.toDbEntity(): YouTubeSubscriptionTable = YouTubeSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id, order = order,
)

private fun YouTubeChannelLog.toDbEntity(): YouTubeChannelLogTable = YouTubeChannelLogTable(
    id = id,
    dateTime = dateTime,
    videoId = videoId,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
)

private fun YouTubeVideo.toDbEntity(): YouTubeVideoTable = YouTubeVideoTable(
    id = id,
    title = title,
    channelId = channel.id,
    scheduledStartDateTime = scheduledStartDateTime,
    scheduledEndDateTime = scheduledEndDateTime,
    actualStartDateTime = actualStartDateTime,
    actualEndDateTime = actualEndDateTime,
    thumbnailUrl = thumbnailUrl,
    description = description,
    viewerCount = viewerCount,
    broadcastContent = liveBroadcastContent,
)

private fun YouTubeChannelDetail.toAddition(): YouTubeChannelAdditionTable =
    YouTubeChannelAdditionTable(
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

private fun YouTubeChannel.toDbEntity(): YouTubeChannelTable = YouTubeChannelTable(
    id = id, title = title, iconUrl = iconUrl,
)

private fun YouTubePlaylistItem.toDbEntity(): YouTubePlaylistItemTable = YouTubePlaylistItemTable(
    id,
    playlistId,
    title,
    channel.id,
    thumbnailUrl,
    videoId,
    description,
    videoOwnerChannelId,
    publishedAt
)

internal interface YouTubeDaoProviders : YouTubeChannelDaoProviders, YouTubeVideoDaoProviders,
    YouTubePlaylistDaoProviders, YouTubeSubscriptionDaoProviders
