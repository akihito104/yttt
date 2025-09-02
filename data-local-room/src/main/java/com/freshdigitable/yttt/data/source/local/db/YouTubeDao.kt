package com.freshdigitable.yttt.data.source.local.db

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionRelevanceOrdered
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.deferForeignKeys
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
        subscriptions: Collection<YouTubeSubscription>,
    ) = db.withTransaction {
        addChannelEntities(subscriptions.map { it.channel })
        addSubscriptionEntities(subscriptions.map { it.toDbEntity() })
        val orders = subscriptions.filterIsInstance<YouTubeSubscriptionRelevanceOrdered>()
            .map { YouTubeSubscriptionRelevanceOrderTable(it.id, it.order) }
        if (orders.isNotEmpty()) {
            addSubscriptionRelevanceOrders(orders)
        }
    }

    suspend fun addChannelRelatedPlaylistList(entities: Collection<YouTubeChannelRelatedPlaylist>) =
        db.withTransaction {
            val playlists = entities.mapNotNull { it.uploadedPlayList }
                .distinct()
                .map { YouTubePlaylistTable(it) }
            addPlaylists(playlists)
            addChannelRelatedPlaylistEntities(entities)
        }

    suspend fun updatePlaylistWithItems(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) = db.withTransaction {
        val p = item.playlist
        if (p !is YouTubePlaylistTable) {
            addPlaylist(YouTubePlaylistTable(p.id, p.title, p.thumbnailUrl))
        }
        addPlaylistExpire(item.toEntity(cacheControl))
        item.eTag?.let {
            addPlaylistWithItemsEtag(YouTubePlaylistWithItemsEtag(p.id, it))
        }
        if (item.items.any { it !is YouTubePlaylistItemDetailDb }) {
            removePlaylistItemEntitiesByPlaylistId(p.id)
            if (item.items.isNotEmpty()) {
                addPlaylistItems(item.items.map { it.toDbEntity() })
                addPlaylistItemAdditions(
                    item.items.filterIsInstance<YouTubePlaylistItemDetail>()
                        .map { YouTubePlaylistItemAdditionTable(it) }
                )
            }
        }
    }

    internal suspend fun removePlaylistItemEntitiesByPlaylistId(id: YouTubePlaylist.Id) {
        removePlaylistItemAdditionsByPlaylistId(id)
        removePlaylistItemsByPlaylistId(id)
    }

    internal suspend fun removePlaylistEntitiesByPlaylistId(id: Collection<YouTubePlaylist.Id>) {
        removePlaylistExpire(id)
        removePlaylistWithItemsEtag(id)
        removePlaylistById(id)
    }

    suspend fun updatePlaylistWithItemsCacheControl(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) {
        addPlaylistExpire(item.toEntity(cacheControl))
    }

    override suspend fun deleteTable() = db.withTransaction {
        db.deferForeignKeys()
        listOf(videoDao, channelDao, subscriptionDao, playlistDao).forEach { it.deleteTable() }
    }
}

private fun YouTubeSubscription.toDbEntity(): YouTubeSubscriptionTable = YouTubeSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id,
)

private fun YouTubePlaylistWithItem<*>.toEntity(cacheControl: CacheControl): YouTubePlaylistExpireTable =
    YouTubePlaylistExpireTable(
        id = playlist.id,
        cacheControl = cacheControl.toDb(),
    )

private fun YouTubePlaylistItem.toDbEntity(): YouTubePlaylistItemTable =
    YouTubePlaylistItemTable(this)

internal interface YouTubeDaoProviders : YouTubeChannelDaoProviders, YouTubeVideoDaoProviders,
    YouTubePlaylistDaoProviders, YouTubeSubscriptionDaoProviders, YouTubePageSourceDaoProviders
