package com.freshdigitable.yttt.data.source.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSubscriptions(subscriptions: Collection<LiveSubscriptionTable>)

    @Query("DELETE FROM subscription WHERE id IN (:removed)")
    suspend fun removeSubscriptions(removed: Collection<LiveSubscription.Id>)

    @Query("SELECT * FROM subscription_view")
    suspend fun findAllSubscriptions(): List<LiveSubscriptionDbView>

    @Query("SELECT * FROM subscription_view")
    fun watchAllSubscriptions(): Flow<List<LiveSubscriptionDbView>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelLogs(logs: Collection<LiveChannelLogTable>)

    @Query(
        "SELECT * FROM channel_log" +
            " WHERE channel_id = :channelId AND datetime >= :publishedAfter" +
            " ORDER BY datetime DESC LIMIT :maxResult"
    )
    suspend fun findChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant,
        maxResult: Long? = Long.MAX_VALUE,
    ): List<LiveChannelLogTable>

    @Query(
        "SELECT * FROM channel_log WHERE channel_id = :channelId" +
            " ORDER BY datetime DESC LIMIT :maxResult"
    )
    suspend fun findChannelLogs(
        channelId: LiveChannel.Id,
        maxResult: Long? = Long.MAX_VALUE
    ): List<LiveChannelLogTable>

    @Query("DELETE FROM channel_log")
    suspend fun removeAllChannelLogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideos(videos: Collection<LiveVideoTable>)

    @Query(
        "SELECT id FROM video AS v WHERE NOT EXISTS" +
            " (SELECT video_id FROM playlist_item AS p WHERE v.id = p.video_id" +
            " UNION SELECT video_id FROM free_chat AS f WHERE v.id = f.video_id)"
    )
    suspend fun findUnusedVideoIds(): List<LiveVideo.Id>

    @Query("DELETE FROM video WHERE id IN (:videoIds)")
    suspend fun removeVideos(videoIds: Collection<LiveVideo.Id>)

    @Query(
        "SELECT v.* FROM (SELECT * FROM video_view WHERE id IN (:ids)) AS v " +
            "INNER JOIN (SELECT * FROM video_expire WHERE :current < expired_at) AS e ON e.video_id = v.id"
    )
    suspend fun findVideosById(
        ids: Collection<LiveVideo.Id>,
        current: Instant = Instant.now(),
    ): List<LiveVideoDbView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLiveVideoExpire(expire: Collection<LiveVideoExpireTable>)

    @Query("DELETE FROM video_expire WHERE video_id IN (:ids)")
    suspend fun removeLiveVideoExpire(ids: Collection<LiveVideo.Id>)

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    suspend fun findAllUnfinishedVideoList(): List<LiveVideoDbView>

    @Query(SQL_FIND_ALL_UNFINISHED_VIDEOS)
    fun watchAllUnfinishedVideos(): Flow<List<LiveVideoDbView>>

    @Query("UPDATE video SET visible = 0 WHERE id IN (:ids)")
    suspend fun updateVideoInvisible(ids: Collection<LiveVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannels(channels: Collection<LiveChannelTable>)

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun findChannel(id: LiveChannel.Id): LiveChannelTable?

    @Query("SELECT * FROM channel_detail WHERE id IN (:id)")
    suspend fun findChannelDetail(id: Collection<LiveChannel.Id>): List<LiveChannelDetailDbView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelAddition(addition: Collection<LiveChannelAdditionTable>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFreeChatItems(entities: Collection<FreeChatTable>)

    @Query("DELETE FROM free_chat WHERE video_id IN(:ids)")
    suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylist(playlist: LivePlaylistTable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylists(playlist: List<LivePlaylistTable>)

    @Transaction
    @Query("SELECT * FROM (SELECT * FROM playlist WHERE :since < (last_modified + max_age)) WHERE id = :id")
    suspend fun findPlaylistById(
        id: LivePlaylist.Id,
        since: Instant = Instant.EPOCH,
    ): LivePlaylistDb?

    @Query("UPDATE playlist SET last_modified = :lastModified, max_age = :maxAge WHERE id = :id")
    suspend fun updatePlaylist(
        id: LivePlaylist.Id,
        lastModified: Instant = Instant.now(),
        maxAge: Duration,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistItems(items: Collection<LivePlaylistItemTable>)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :id")
    suspend fun removePlaylistItemsByPlaylistId(id: LivePlaylist.Id)

    companion object {
        private const val CONDITION_UNFINISHED_VIDEOS =
            "(schedule_start_datetime NOTNULL OR actual_start_datetime NOTNULL) " +
                "AND actual_end_datetime ISNULL"
        private const val SQL_FIND_ALL_UNFINISHED_VIDEOS = "SELECT * FROM video_view " +
            "WHERE $CONDITION_UNFINISHED_VIDEOS"
    }
}
