package com.freshdigitable.yttt.data.source.local

import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelSection
import com.freshdigitable.yttt.data.model.YouTubeId
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemDetails
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeChannelDataSource
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.YouTubePlaylistDataSource
import com.freshdigitable.yttt.data.source.YouTubeSubscriptionDataSource
import com.freshdigitable.yttt.data.source.YouTubeVideoDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class YouTubeLocalDataSource @Inject constructor(
    private val dao: YouTubeDao,
    private val channelDataSource: YouTubeChannelLocalDataSource,
    private val videoDataSource: YouTubeVideoLocalDataSource,
    private val subscriptionDataSource: YouTubeSubscriptionLocalDataSource,
    private val playlistDataSource: YouTubePlaylistLocalDataSource,
) : YouTubeDataSource.Local,
    YouTubeChannelDataSource.Local by channelDataSource,
    YouTubeVideoDataSource.Local by videoDataSource,
    YouTubeSubscriptionDataSource.Local by subscriptionDataSource,
    YouTubePlaylistDataSource.Local by playlistDataSource,
    ImageDataSource by videoDataSource {
    override suspend fun fetchLiveChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant?,
        maxResult: Long?,
    ): Result<List<YouTubeChannelLog>> = runCatching {
        if (publishedAfter != null) {
            dao.findChannelLogs(channelId, publishedAfter, maxResult)
        } else {
            dao.findChannelLogs(channelId, maxResult)
        }
    }

    override suspend fun addChannelLogs(channelLogs: Collection<YouTubeChannelLog>) {
        dao.addChannelLogEntities(channelLogs)
    }

    private val channelSections = mutableMapOf<YouTubeChannel.Id, List<YouTubeChannelSection>>()
    override suspend fun fetchChannelSection(id: YouTubeChannel.Id): Result<List<YouTubeChannelSection>> {
        return Result.success(channelSections[id] ?: emptyList())
    }

    override suspend fun addChannelSection(channelSection: Collection<YouTubeChannelSection>) {
        if (channelSection.isEmpty()) {
            return
        }
        channelSections[channelSection.first().channelId] = channelSection.toList()
    }
}

@Singleton
internal class YouTubeExtendedDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    private val videoDataSource: YouTubeVideoLocalDataSource,
    private val subscriptionDataSource: YouTubeSubscriptionLocalDataSource,
    private val playlistDataSource: YouTubePlaylistLocalDataSource,
) : YouTubeDataSource.Extended,
    YouTubeVideoDataSource.Extended by videoDataSource,
    YouTubeSubscriptionDataSource.Extended by subscriptionDataSource,
    YouTubePlaylistDataSource.Extended by playlistDataSource,
    ImageDataSource by videoDataSource {
    override suspend fun cleanUp() {
        database.youTubeChannelLogDao.deleteTable()
        removePlaylistWithItemsEntities()
        removeUnusedChannels()
        removeNotExistVideos()
    }

    private suspend fun removePlaylistWithItemsEntities() = database.withTransaction {
        val playlists = dao.fetchPlaylistByUploadedPlaylist()
        if (playlists.isEmpty()) {
            return@withTransaction
        }
        val videoIds = dao.findVideoByPlaylistIds(playlists)
        dao.removePlaylistWithItemsEntitiesByPlaylistIds(playlists)
        if (videoIds.isNotEmpty()) {
            removeVideo(videoIds.toSet())
        }
    }

    private suspend fun removeNotExistVideos() {
        database.withTransaction {
            val archivedIds = dao.findAllArchivedVideos().toSet()
            updateAsArchivedVideo(archivedIds)
        }
        database.withTransaction {
            val removingId = dao.findUnusedVideoIds().toSet()
            removeVideo(removingId)
        }
    }

    private suspend fun removeUnusedChannels() = database.withTransaction {
        database.deferForeignKeys()
        val channelIds = dao.findUnsubscribedChannelIds().toSet()
        if (channelIds.isEmpty()) {
            return@withTransaction
        }
        val videoIds = dao.findVideoIdsByChannelId(channelIds)
        val playlists = dao.findChannelRelatedPlaylists(channelIds)
        dao.removePlaylistWithItemsEntitiesByPlaylistIds(playlists.mapNotNull { it.uploadedPlayList })
        removeVideo(videoIds.toSet())
        dao.removeChannelEntities(channelIds)
    }

    override suspend fun deleteAllTables() {
        dao.deleteTable()
    }
}

@Singleton
internal class YouTubeVideoLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    imageDataSource: ImageDataSource,
    private val ioScope: IoScope,
) : YouTubeVideoDataSource.Local, YouTubeVideoDataSource.Extended, ImageDataSource by imageDataSource {
    override suspend fun fetchVideoList(ids: Set<YouTubeVideo.Id>): Result<List<Updatable<YouTubeVideoExtended>>> =
        if (ids.isEmpty()) {
            Result.success(emptyList())
        } else {
            ioScope.asResult { fetchByIds(ids) { findVideosById(it) }.flatten() }
        }

    override suspend fun addFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItemEntities(ids, true, YouTubeVideo.MAX_AGE_FREE_CHAT)
    }

    override suspend fun removeFreeChatItems(ids: Set<YouTubeVideo.Id>) {
        dao.addFreeChatItemEntities(ids, false, YouTubeVideo.MAX_AGE_DEFAULT)
    }

    override suspend fun addPinnedVideo(id: YouTubeVideo.Id) {
        dao.addPinnedVideo(id)
    }

    override suspend fun removePinnedVideo(id: YouTubeVideo.Id) {
        dao.removePinnedVideo(id)
    }

    override suspend fun addVideo(video: Collection<Updatable<YouTubeVideoExtended>>) =
        ioScope.asResult { dao.addVideoEntities(video) }.getOrThrow()

    override suspend fun fetchUpdatableVideoIds(current: Instant): List<YouTubeVideo.Id> =
        dao.fetchUpdatableVideoIds(current)

    override suspend fun updateAsArchivedVideo(ids: Set<YouTubeVideo.Id>): Unit = ioScope.asResult {
        fetchByIds(ids) {
            val thumbs = findThumbnailUrlByIds(it)
            dao.updateAsArchivedVideoEntities(it)
            removeImageByUrl(thumbs)
        }.forEach { _ -> }
    }.getOrThrow()

    override suspend fun removeVideo(ids: Set<YouTubeVideo.Id>): Unit = ioScope.asResult {
        fetchByIds(ids) {
            val thumbs = findThumbnailUrlByIds(it)
            removeImageByUrl(thumbs)
            removeVideoEntities(it)
        }.forEach { _ -> }
    }.getOrThrow()

    private suspend fun <I : YouTubeId, O> fetchByIds(ids: Set<I>, query: suspend YouTubeDao.(Set<I>) -> O): List<O> =
        if (ids.isEmpty()) {
            emptyList()
        } else if (ids.size < YouTubeDataSource.MAX_BATCH_SIZE) {
            listOf(dao.query(ids))
        } else {
            val a = database.withTransaction {
                ids.chunked(YouTubeDataSource.MAX_BATCH_SIZE).map { dao.query(it.toSet()) }
            }
            listOf(a).flatten()
        }
}

@Singleton
internal class YouTubeSubscriptionLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    private val ioScope: IoScope,
) : YouTubeSubscriptionDataSource.Local, YouTubeSubscriptionDataSource.Extended {
    override var subscriptionsFetchedAt: Instant = Instant.EPOCH
    override var subscriptionsRelevanceOrderedFetchedAt: Instant = Instant.EPOCH
    override suspend fun fetchSubscriptionIds(): Set<YouTubeSubscription.Id> = dao.fetchAllSubscriptionIds().toSet()

    override suspend fun findSubscriptionSummaries(
        ids: Collection<YouTubeSubscription.Id>,
    ): List<YouTubeSubscriptionSummary> = ioScope.asResult { dao.findSubscriptionSummaries(ids) }.getOrThrow()

    override suspend fun findSubscriptionSummariesByOffset(
        offset: Int,
        pageSize: Int,
    ): List<YouTubeSubscriptionSummary> = ioScope.asResult {
        dao.findSubscriptionSummariesByOffset(offset, pageSize)
    }.getOrThrow()

    override suspend fun addPagedSubscription(subscription: Collection<YouTubeSubscription>) {
        dao.addSubscriptionList(subscription)
    }

    override suspend fun findSubscriptionQuery(offset: Int): YouTubeSubscriptionQuery? =
        dao.findSubscriptionQuery(offset)

    override suspend fun syncSubscriptionList(
        subscriptions: Set<YouTubeSubscription.Id>,
        query: List<YouTubeSubscriptionQuery>,
    ) = database.withTransaction {
        if (query.isNotEmpty()) {
            dao.addSubscriptionQuery(query)
        }
        val subs = dao.findSubscriptionIdsByRemainingIds(subscriptions)
        dao.removeSubscriptionEntities(subs)
    }

    override suspend fun fetchPagedSubscription(
        query: YouTubeSubscriptionQuery,
    ): Result<NetworkResponse<List<YouTubeSubscription>>> = throw NotImplementedError()
}

@Singleton
internal class YouTubePlaylistLocalDataSource @Inject constructor(
    private val dao: YouTubeDao,
    private val ioScope: IoScope,
) : YouTubePlaylistDataSource.Local, YouTubePlaylistDataSource.Extended {
    override suspend fun fetchPlaylistWithItems(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItems>?> = ioScope.asResult {
        dao.findPlaylistWithItemIds(id)?.toUpdatable()
    }

    override suspend fun fetchPlaylistWithItemDetails(
        id: YouTubePlaylist.Id,
        maxResult: Long,
        cache: YouTubePlaylistWithItem<*>?,
    ): Result<Updatable<YouTubePlaylistWithItemDetails>?> = ioScope.asResult {
        dao.findPlaylistWithItemDetails(id, maxResult, cache)
    }

    private val playlist = mutableMapOf<YouTubePlaylist.Id, Updatable<YouTubePlaylist>>()
    override suspend fun fetchPlaylist(ids: Set<YouTubePlaylist.Id>): Result<List<Updatable<YouTubePlaylist>>> =
        Result.success(ids.mapNotNull { playlist[it] })

    override suspend fun addPlaylist(playlist: Collection<Updatable<YouTubePlaylist>>) {
        this.playlist.putAll(playlist.associateBy { it.item.id })
    }

    override suspend fun updatePlaylistWithItems(item: YouTubePlaylistWithItem<*>, cacheControl: CacheControl) {
        dao.updatePlaylistWithItems(item, cacheControl)
    }

    override suspend fun updatePlaylistWithItemsCacheControl(
        item: YouTubePlaylistWithItem<*>,
        cacheControl: CacheControl,
    ) {
        dao.updatePlaylistWithItemsCacheControl(item, cacheControl)
    }
}

internal class YouTubeChannelLocalDataSource @Inject constructor(
    private val database: AppDatabase,
    private val dao: YouTubeDao,
    private val ioScope: IoScope,
) : YouTubeChannelDataSource.Local {
    override suspend fun fetchChannelList(ids: Set<YouTubeChannel.Id>): Result<List<YouTubeChannel>> =
        if (ids.isEmpty()) {
            Result.success(emptyList())
        } else {
            ioScope.asResult { dao.findChannels(ids) }
        }

    override suspend fun fetchChannelRelatedPlaylistList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<YouTubeChannelRelatedPlaylist>> = if (ids.isEmpty()) {
        Result.success(emptyList())
    } else {
        ioScope.asResult { dao.findChannelRelatedPlaylists(ids) }
    }

    override suspend fun fetchChannelDetailList(
        ids: Set<YouTubeChannel.Id>,
    ): Result<List<Updatable<YouTubeChannelDetail>>> = if (ids.isEmpty()) {
        Result.success(emptyList())
    } else {
        ioScope.asResult { dao.findChannelDetail(ids) }
    }

    override suspend fun addChannelList(channel: Collection<YouTubeChannel>) {
        dao.addChannelEntities(channel)
    }

    override suspend fun addChannelRelatedPlaylists(channel: List<YouTubeChannelRelatedPlaylist>) {
        dao.addChannelRelatedPlaylistList(channel)
    }

    override suspend fun addChannelDetailList(channelDetail: Collection<Updatable<YouTubeChannelDetail>>) =
        database.withTransaction {
            dao.addChannelDetails(channelDetail)
            dao.addChannelRelatedPlaylistList(channelDetail.map { it.item })
        }
}
