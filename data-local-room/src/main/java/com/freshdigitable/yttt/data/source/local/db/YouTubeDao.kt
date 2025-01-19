package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
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

    suspend fun addVideos(videos: Collection<YouTubeVideoExtended>) = db.withTransaction {
        val v = videos.filter { it !is YouTubeVideoDb }
        val entity = v.map { it.toDbEntity() }
        val freeChat = v.map { FreeChatTable(it.id, it.isFreeChat) }
        val expiring = v.map { YouTubeVideoExpireTable(it.id, it.updatableAt) }
        addVideoEntities(entity)
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
        updatableAt: Instant,
    ) = db.withTransaction {
        val entities = ids.map { FreeChatTable(it, isFreeChat = isFreeChat) }
        val expires = ids.map { YouTubeVideoExpireTable(it, updatableAt) }
        addFreeChatItemEntities(entities)
        addLiveVideoExpire(expires)
    }

    suspend fun updatePlaylistWithItems(updatable: YouTubePlaylistWithItems) = db.withTransaction {
        if (updatable.playlist is YouTubePlaylistTable) {
            updatePlaylist(
                id = updatable.playlist.id,
                lastModified = updatable.fetchedAt,
                maxAge = updatable.maxAge,
            )
        } else {
            addPlaylist(updatable.toEntity())
        }
        if (updatable.items.any { it !is YouTubePlaylistItemDb }) {
            removePlaylistItemsByPlaylistId(updatable.playlist.id)
            if (updatable.items.isNotEmpty()) {
                addPlaylistItems(updatable.items.map { it.toDbEntity() })
            }
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

private fun YouTubePlaylistWithItems.toEntity(): YouTubePlaylistTable = YouTubePlaylistTable(
    id = playlist.id,
    fetchedAt = fetchedAt,
    maxAge = maxAge,
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
