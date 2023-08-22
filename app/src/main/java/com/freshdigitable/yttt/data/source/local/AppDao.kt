package com.freshdigitable.yttt.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import kotlinx.coroutines.flow.Flow
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

    @Query("DELETE FROM video WHERE NOT ($CONDITION_UNFINISHED_VIDEOS)")
    suspend fun removeAllFinishedVideos()

    @Query("SELECT * FROM video_view WHERE id IN (:ids)")
    suspend fun findVideosById(ids: Collection<LiveVideo.Id>): List<LiveVideoDbView>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFreeChatItems(ids: Collection<FreeChatTable>)

    @Query("DELETE FROM free_chat WHERE video_id IN(:ids)")
    suspend fun removeFreeChatItems(ids: Collection<LiveVideo.Id>)

    companion object {
        private const val CONDITION_UNFINISHED_VIDEOS =
            "(schedule_start_datetime NOTNULL OR actual_start_datetime NOTNULL) " +
                "AND actual_end_datetime ISNULL"
        private const val SQL_FIND_ALL_UNFINISHED_VIDEOS = "SELECT * FROM video_view " +
            "WHERE $CONDITION_UNFINISHED_VIDEOS"
    }
}
