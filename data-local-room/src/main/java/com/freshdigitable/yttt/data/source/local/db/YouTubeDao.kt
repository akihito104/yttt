package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
import javax.inject.Inject

internal class YouTubeDao @Inject constructor(
    private val db: AppDatabase,
    private val videoDao: YouTubeVideoDaoImpl,
    private val playlistDao: YouTubePlaylistDaoImpl,
    private val subscriptionDao: YouTubeSubscriptionDaoImpl,
    private val channelDao: YouTubeChannelDaoImpl,
) : YouTubeVideoDao by videoDao,
    YouTubePlaylistDao by playlistDao,
    YouTubeSubscriptionDao by subscriptionDao,
    YouTubeChannelDao by channelDao {
    suspend fun addSubscriptionList(
        subscriptions: Collection<YouTubeSubscription>,
    ) = db.withTransaction {
        addChannelEntities(subscriptions.map { it.channel })
        addSubscriptionEntities(subscriptions)
    }

    suspend fun addChannelRelatedPlaylistList(entities: Collection<YouTubeChannelRelatedPlaylist>) =
        db.withTransaction {
            addPlaylistEntities(entities.mapNotNull { it.uploadedPlayList })
            addChannelRelatedPlaylistEntities(entities)
        }

    override suspend fun updatePlaylistWithItems(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) = db.withTransaction {
        videoDao.insertOrIgnoreVideoEntities(item.items.map { it.videoId }.toSet())
        playlistDao.updatePlaylistWithItems(item, cacheControl)
    }

    override suspend fun removeVideoEntities(
        videoIds: Collection<YouTubeVideo.Id>,
    ) = db.withTransaction {
        playlistDao.removePlaylistItemsByVideoIds(videoIds)
        videoDao.removeVideoEntities(videoIds)
    }

    override suspend fun deleteTable() = db.withTransaction {
        db.deferForeignKeys()
        listOf(videoDao, channelDao, subscriptionDao, playlistDao).forEach { it.deleteTable() }
    }
}

internal interface YouTubeDaoProviders :
    YouTubeChannelDaoProviders,
    YouTubeVideoDaoProviders,
    YouTubePlaylistDaoProviders,
    YouTubeSubscriptionDaoProviders,
    YouTubePageSourceDaoProviders
