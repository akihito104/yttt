package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
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
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

@Dao
internal interface YouTubeDao {
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

    @Query(
        "SELECT s.*, c.title AS channel_title, c.icon AS channel_icon FROM subscription AS s " +
            "INNER JOIN channel AS c ON c.id = s.channel_id ORDER BY subs_order ASC"
    )
    suspend fun findAllSubscriptions(): List<YouTubeSubscriptionDb>

    @Query(
        "SELECT s.id AS subscription_id, s.channel_id, c.uploaded_playlist_id, " +
            "(c.last_modified + c.max_age) AS playlist_expired_at FROM subscription AS s " +
            "LEFT OUTER JOIN ( " +
            " SELECT c.id, c.uploaded_playlist_id, p.max_age, p.last_modified FROM channel_addition AS c " +
            " INNER JOIN playlist AS p ON c.uploaded_playlist_id = p.id " +
            ") AS c ON s.channel_id = c.id"
    )
    suspend fun findAllSubscriptionSummary(): List<YouTubeSubscriptionSummaryDb>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelLogEntities(logs: Collection<YouTubeChannelLogTable>)

    @Transaction
    suspend fun addChannelLogs(logs: Collection<YouTubeChannelLog>, current: Instant) {
        val channels = logs.map { it.channelId }.distinct()
            .filter { findChannel(it) == null }
            .map { YouTubeChannelTable(id = it) }
        val vIds = logs.map { it.videoId }.toSet()
        val found = findVideosById(vIds, current).map { it.id }.toSet()
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

    @Transaction
    suspend fun addVideos(videos: Map<YouTubeVideo, Instant>) {
        val v = videos.keys.map { it.toDbEntity() }
        val expiring = videos.entries.map { (v, e) -> YouTubeVideoExpireTable(v.id, e) }
        addVideoEntities(v)
        addLiveVideoExpire(expiring)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideoIsArchivedEntities(items: Collection<YouTubeVideoIsArchivedTable>)

    @Query("DELETE FROM yt_video_is_archived WHERE video_id IN (:ids)")
    suspend fun removeVideoIsArchivedEntities(ids: Collection<YouTubeVideo.Id>)

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
        "SELECT v.*, c.id AS c_id, c.icon AS c_icon, c.title AS c_title, f.is_free_chat FROM (SELECT * FROM video WHERE id IN (:ids)) AS v " +
            "INNER JOIN (SELECT * FROM video_expire WHERE :current < expired_at) AS e ON e.video_id = v.id " +
            "INNER JOIN channel AS c ON c.id = v.channel_id " +
            "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id"
    )
    suspend fun findVideosById(
        ids: Collection<YouTubeVideo.Id>,
        current: Instant,
    ): List<YouTubeVideoDb>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLiveVideoExpire(expire: Collection<YouTubeVideoExpireTable>)

    @Query("DELETE FROM video_expire WHERE video_id IN (:ids)")
    suspend fun removeLiveVideoExpire(ids: Collection<YouTubeVideo.Id>)

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    suspend fun findAllUnfinishedVideoList(): List<YouTubeVideoDb>

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    fun watchAllUnfinishedVideos(): Flow<List<YouTubeVideoDb>>

    @Query("SELECT id FROM video WHERE NOT ($CONDITION_UNFINISHED_VIDEOS)")
    suspend fun findAllArchivedVideos(): List<YouTubeVideo.Id>

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

    @Query("SELECT c.icon, c.title, a.* FROM channel AS c INNER JOIN channel_addition AS a ON c.id = a.id WHERE c.id IN (:id)")
    suspend fun findChannelDetail(id: Collection<YouTubeChannel.Id>): List<YouTubeChannelDetailDb>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelAddition(addition: Collection<YouTubeChannelAdditionTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFreeChatItemEntities(entities: Collection<FreeChatTable>)

    @Transaction
    suspend fun addFreeChatItems(
        ids: Collection<YouTubeVideo.Id>,
        isFreeChat: Boolean,
        expiredAt: Instant,
    ) {
        val entities = ids.map { FreeChatTable(it, isFreeChat = isFreeChat) }
        val expires = ids.map { YouTubeVideoExpireTable(it, expiredAt) }
        addFreeChatItemEntities(entities)
        addLiveVideoExpire(expires)
    }

    @Query("SELECT * FROM free_chat WHERE video_id IN (:ids)")
    suspend fun findFreeChatItems(ids: Collection<YouTubeVideo.Id>): List<FreeChatTable>

    @Query("DELETE FROM free_chat WHERE video_id IN(:ids)")
    suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylist(playlist: YouTubePlaylistTable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylists(playlist: List<YouTubePlaylistTable>)

    @Query("SELECT * FROM (SELECT * FROM playlist WHERE :since < (last_modified + max_age)) WHERE id = :id")
    suspend fun findPlaylistById(
        id: YouTubePlaylist.Id,
        since: Instant = Instant.EPOCH,
    ): YouTubePlaylistTable?

    @Query("SELECT * FROM yt_playlist_item_summary AS s WHERE s.playlist_id = :id LIMIT :maxResult")
    suspend fun findPlaylistItemSummary(
        id: YouTubePlaylist.Id,
        maxResult: Long,
    ): List<YouTubePlaylistItemSummaryDb>

    @Query("UPDATE playlist SET last_modified = :lastModified, max_age = :maxAge WHERE id = :id")
    suspend fun updatePlaylist(
        id: YouTubePlaylist.Id,
        lastModified: Instant,
        maxAge: Duration,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistItems(items: Collection<YouTubePlaylistItemTable>)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :id")
    suspend fun removePlaylistItemsByPlaylistId(id: YouTubePlaylist.Id)

    @Transaction
    suspend fun setPlaylistItems(
        id: YouTubePlaylist.Id,
        lastModified: Instant,
        maxAge: Duration? = null,
        items: Collection<YouTubePlaylistItem>,
    ) {
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

    @Query(
        "SELECT p.*, c.icon AS channel_icon, c.title AS channel_title FROM playlist_item AS p " +
            "INNER JOIN channel AS c ON c.id = p.channel_id WHERE p.playlist_id = :id"
    )
    suspend fun findPlaylistItemByPlaylistId(id: YouTubePlaylist.Id): List<YouTubePlaylistItemDb>

    companion object {
        private const val CONDITION_UNFINISHED_VIDEOS =
            "(schedule_start_datetime NOTNULL OR actual_start_datetime NOTNULL) " +
                "AND actual_end_datetime ISNULL"
        private const val SQL_VIDEOS =
            "SELECT v.*, c.id AS c_id, c.icon AS c_icon, c.title AS c_title, f.is_free_chat FROM video AS v " +
                "INNER JOIN channel AS c ON v.channel_id = c.id " +
                "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id"
        private const val SQL_FIND_ALL_UNFINISHED_VIDEOS =
            "$SQL_VIDEOS WHERE $CONDITION_UNFINISHED_VIDEOS"
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

internal data class YouTubeVideoDb(
    @Embedded
    private val video: YouTubeVideoTable,
    @Embedded("c_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo("is_free_chat")
    override val isFreeChat: Boolean?,
) : YouTubeVideo {
    override val id: YouTubeVideo.Id
        get() = video.id
    override val title: String
        get() = video.title
    override val thumbnailUrl: String
        get() = video.thumbnailUrl
    override val scheduledStartDateTime: Instant?
        get() = video.scheduledStartDateTime
    override val scheduledEndDateTime: Instant?
        get() = video.scheduledEndDateTime
    override val actualStartDateTime: Instant?
        get() = video.actualStartDateTime
    override val actualEndDateTime: Instant?
        get() = video.actualEndDateTime
    override val description: String
        get() = video.description
    override val viewerCount: BigInteger?
        get() = video.viewerCount
}
