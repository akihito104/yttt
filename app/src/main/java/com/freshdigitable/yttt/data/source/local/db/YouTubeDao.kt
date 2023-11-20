package com.freshdigitable.yttt.data.source.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeVideo
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

@Dao
interface YouTubeDao {
    @Transaction
    suspend fun addSubscriptions(subscriptions: Collection<YouTubeSubscription>) {
        val channels = subscriptions.map { it.channel }.toSet()
            .map { it.toDbEntity() }
        addChannels(channels)
        addSubscriptionEntities(subscriptions.map { it.toDbEntity() })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSubscriptionEntities(subscriptions: Collection<YouTubeSubscriptionTable>)

    @Query("DELETE FROM subscription WHERE id IN (:removed)")
    suspend fun removeSubscriptions(removed: Collection<YouTubeSubscription.Id>)

    @Query("SELECT * FROM subscription_view")
    suspend fun findAllSubscriptions(): List<YouTubeSubscriptionDbView>

    @Query("SELECT * FROM subscription_view")
    fun watchAllSubscriptions(): Flow<List<YouTubeSubscriptionDbView>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelLogEntities(logs: Collection<YouTubeChannelLogTable>)

    @Transaction
    suspend fun addChannelLogs(logs: Collection<YouTubeChannelLog>) {
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

    @Query(
        "SELECT * FROM channel_log" +
            " WHERE channel_id = :channelId AND datetime >= :publishedAfter" +
            " ORDER BY datetime DESC LIMIT :maxResult"
    )
    suspend fun findChannelLogs(
        channelId: YouTubeChannel.Id,
        publishedAfter: Instant,
        maxResult: Long? = Long.MAX_VALUE,
    ): List<YouTubeChannelLogTable>

    @Query(
        "SELECT * FROM channel_log WHERE channel_id = :channelId" +
            " ORDER BY datetime DESC LIMIT :maxResult"
    )
    suspend fun findChannelLogs(
        channelId: YouTubeChannel.Id,
        maxResult: Long? = Long.MAX_VALUE
    ): List<YouTubeChannelLogTable>

    @Query("DELETE FROM channel_log")
    suspend fun removeAllChannelLogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideoEntities(videos: Collection<YouTubeVideoTable>)

    suspend fun addVideos(videos: Map<YouTubeVideo, Instant>) {
        val v = videos.keys.map { it.toDbEntity() }
        val expiring = videos.entries.map { (v, e) -> YouTubeVideoExpireTable(v.id, e) }
        addVideoEntities(v)
        addLiveVideoExpire(expiring)
    }

    @Query(
        "SELECT id FROM video AS v WHERE NOT EXISTS" +
            " (SELECT video_id FROM playlist_item AS p WHERE v.id = p.video_id" +
            " UNION SELECT video_id FROM free_chat AS f WHERE v.id = f.video_id)"
    )
    suspend fun findUnusedVideoIds(): List<YouTubeVideo.Id>

    @Query("DELETE FROM video WHERE id IN (:videoIds)")
    suspend fun removeVideoEntities(videoIds: Collection<YouTubeVideo.Id>)

    @Transaction
    suspend fun removeVideos(videoIds: Collection<YouTubeVideo.Id>) {
        removeFreeChatItems(videoIds)
        removeLiveVideoExpire(videoIds)
        removeVideoEntities(videoIds)
    }

    @Query(
        "SELECT v.* FROM (SELECT * FROM video_view WHERE id IN (:ids)) AS v " +
            "INNER JOIN (SELECT * FROM video_expire WHERE :current < expired_at) AS e ON e.video_id = v.id"
    )
    suspend fun findVideosById(
        ids: Collection<YouTubeVideo.Id>,
        current: Instant = Instant.now(),
    ): List<YouTubeVideoDbView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLiveVideoExpire(expire: Collection<YouTubeVideoExpireTable>)

    @Query("DELETE FROM video_expire WHERE video_id IN (:ids)")
    suspend fun removeLiveVideoExpire(ids: Collection<YouTubeVideo.Id>)

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    suspend fun findAllUnfinishedVideoList(): List<YouTubeVideoDbView>

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    fun watchAllUnfinishedVideos(): Flow<List<YouTubeVideoDbView>>

    @Query("UPDATE video SET visible = 0 WHERE id IN (:ids)")
    suspend fun updateVideoInvisible(ids: Collection<YouTubeVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannels(channels: Collection<YouTubeChannelTable>)

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun findChannel(id: YouTubeChannel.Id): YouTubeChannelTable?

    @Transaction
    suspend fun addChannelDetails(channelDetail: Collection<YouTubeChannelDetail>) {
        val channels = channelDetail.map { it.toDbEntity() }
        val additions = channelDetail.map { it.toAddition() }
        val playlists = additions.mapNotNull { it.uploadedPlayList }
            .distinct()
            .map { YouTubePlaylistTable(it) }
        addChannels(channels)
        addPlaylists(playlists)
        addChannelAddition(additions)
    }

    @Query("SELECT * FROM channel_detail WHERE id IN (:id)")
    suspend fun findChannelDetail(id: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetailDbView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelAddition(addition: Collection<YouTubeChannelAdditionTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFreeChatItems(entities: Collection<FreeChatTable>)

    @Query("SELECT * FROM free_chat WHERE video_id IN (:ids)")
    suspend fun findFreeChatItems(ids: Collection<YouTubeVideo.Id>): List<FreeChatTable>

    @Query("DELETE FROM free_chat WHERE video_id IN(:ids)")
    suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylist(playlist: YouTubePlaylistTable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylists(playlist: List<YouTubePlaylistTable>)

    @Transaction
    @Query("SELECT * FROM (SELECT * FROM playlist WHERE :since < (last_modified + max_age)) WHERE id = :id")
    suspend fun findPlaylistById(
        id: YouTubePlaylist.Id,
        since: Instant = Instant.EPOCH,
    ): YouTubePlaylistDb?

    @Query("UPDATE playlist SET last_modified = :lastModified, max_age = :maxAge WHERE id = :id")
    suspend fun updatePlaylist(
        id: YouTubePlaylist.Id,
        lastModified: Instant = Instant.now(),
        maxAge: Duration,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistItems(items: Collection<YouTubePlaylistItemTable>)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :id")
    suspend fun removePlaylistItemsByPlaylistId(id: YouTubePlaylist.Id)

    @Transaction
    suspend fun setPlaylistItems(
        id: YouTubePlaylist.Id,
        lastModified: Instant = Instant.now(),
        maxAge: Duration? = null,
        items: Collection<YouTubePlaylistItem>,
    ) {
        if (items.isEmpty()) {
            addPlaylist(YouTubePlaylistTable.createWithMaxAge(id))
        } else if (maxAge == null) {
            addPlaylist(YouTubePlaylistTable(id))
        } else {
            updatePlaylist(id, maxAge = maxAge)
        }
        removePlaylistItemsByPlaylistId(id)
        if (items.isNotEmpty()) {
            addPlaylistItems(items.map { it.toDbEntity() })
        }
    }

    companion object {
        private const val CONDITION_UNFINISHED_VIDEOS =
            "(schedule_start_datetime NOTNULL OR actual_start_datetime NOTNULL) " +
                "AND actual_end_datetime ISNULL"
        private const val SQL_FIND_ALL_UNFINISHED_VIDEOS = "SELECT * FROM video_view " +
            "WHERE $CONDITION_UNFINISHED_VIDEOS"
    }
}

private fun YouTubeSubscription.toDbEntity(): YouTubeSubscriptionTable = YouTubeSubscriptionTable(
    id = id, subscribeSince = subscribeSince, channelId = channel.id,
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
