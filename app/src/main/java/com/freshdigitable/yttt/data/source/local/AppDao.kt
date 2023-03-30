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

    @Query("SELECT * FROM channel_log WHERE channel_id = :channelId AND datetime >= :publishedAfter")
    suspend fun findChannelLogs(
        channelId: LiveChannel.Id,
        publishedAfter: Instant
    ): List<LiveChannelLogTable>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideos(videos: Collection<LiveVideoTable>)

    @Query("SELECT * FROM video_view WHERE id IN (:ids)")
    suspend fun findVideosById(ids: Collection<LiveVideo.Id>): List<LiveVideoDbView>

    @Query("SELECT * FROM video_view WHERE schedule_start_datetime NOTNULL AND actual_end_datetime ISNULL")
    suspend fun findAllUnfinishedVideoList(): List<LiveVideoDbView>

    @Query("SELECT * FROM video_view WHERE schedule_start_datetime NOTNULL AND actual_end_datetime ISNULL")
    fun watchAllUnfinishedVideos(): Flow<List<LiveVideoDbView>>

    @Query("UPDATE video SET visible = false WHERE id IN (:ids)")
    suspend fun updateVideoInvisible(ids: Collection<LiveVideo.Id>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannels(channels: Collection<LiveChannelTable>)

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun findChannel(id: LiveChannel.Id): LiveChannelTable?
}
