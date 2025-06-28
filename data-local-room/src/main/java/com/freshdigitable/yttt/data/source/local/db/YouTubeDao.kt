package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.Updatable
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
import java.time.Duration
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
        val vIds = logs.filter { it.videoId != null }.associateBy { it.videoId }
        val found = findVideosById(vIds.keys.filterNotNull()).map { it.item.id }.toSet()
        val needed = vIds.keys.filterNotNull() - found
        val videos = needed.mapNotNull { vIds[it] }
            .map {
                YouTubeVideoTable(
                    id = checkNotNull(it.videoId),
                    channelId = it.channelId,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        addChannels(channels)
        addVideoEntities(videos)
        addChannelLogEntities(logs.filter { it.videoId != null }.map { it.toDbEntity() })
    }

    suspend fun addVideos(
        videos: Collection<Updatable<YouTubeVideoExtended>>,
    ) = db.withTransaction {
        val v = videos.filter { it.item !is YouTubeVideoDb }
        val entity = v.map { it.item.toDbEntity() }
        val freeChat = v.map { FreeChatTable(it.item.id, it.item.isFreeChat) }
        val expiring = v.map { YouTubeVideoExpireTable(it.item.id, it.cacheControl.toDb()) }
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
        channelDetail: Collection<Updatable<YouTubeChannelDetail>>,
    ) = db.withTransaction {
        val channels = channelDetail.map { it.item.toDbEntity() }
        val additions = channelDetail.map { it.item.toAddition() }
        val playlists = additions.mapNotNull { it.uploadedPlayList }
            .distinct()
            .map { YouTubePlaylistTable(it) }
        val expired = channelDetail
            .map { YouTubeChannelAdditionExpireTable(it.item.id, it.cacheControl.toDb()) }
        addChannels(channels)
        addPlaylists(playlists)
        addChannelAddition(additions)
        addChannelAdditionExpire(expired)
    }

    suspend fun addFreeChatItems(
        ids: Collection<YouTubeVideo.Id>,
        isFreeChat: Boolean,
        maxAge: Duration,
    ) = db.withTransaction {
        val entities = ids.map { FreeChatTable(it, isFreeChat = isFreeChat) }
        addFreeChatItemEntities(entities)
        updateMaxAgeById(ids, maxAge)
    }

    suspend fun updatePlaylistWithItems(
        updatable: Updatable<YouTubePlaylistWithItems>,
    ) = db.withTransaction {
        if (updatable.item.playlist is YouTubePlaylistTable) {
            updatePlaylist(
                id = updatable.item.playlist.id,
                lastModified = checkNotNull(updatable.cacheControl.fetchedAt),
                maxAge = checkNotNull(updatable.cacheControl.maxAge),
            )
        } else {
            addPlaylist(updatable.toEntity())
        }
        if (updatable.item.items.any { it !is YouTubePlaylistItemDb }) {
            removePlaylistItemsByPlaylistId(updatable.item.playlist.id)
            if (updatable.item.items.isNotEmpty()) {
                addPlaylistItems(updatable.item.items.map { it.toDbEntity() })
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
    videoId = checkNotNull(videoId),
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

internal fun YouTubeChannel.toDbEntity(): YouTubeChannelTable = YouTubeChannelTable(
    id = id, title = title, iconUrl = iconUrl,
)

private fun Updatable<YouTubePlaylistWithItems>.toEntity(): YouTubePlaylistTable =
    YouTubePlaylistTable(
        id = item.playlist.id,
        cacheControl = YouTubePlaylistCacheControlDb(
            checkNotNull(cacheControl.fetchedAt),
            checkNotNull(cacheControl.maxAge),
        ),
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
    YouTubePlaylistDaoProviders, YouTubeSubscriptionDaoProviders, YouTubePageSourceDaoProviders
