package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.math.BigInteger
import java.time.Instant

@Entity(
    tableName = "video",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
internal class YouTubeVideoTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: YouTubeVideo.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    val title: String = "",
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "schedule_start_datetime")
    val scheduledStartDateTime: Instant? = null,
    @ColumnInfo(name = "schedule_end_datetime")
    val scheduledEndDateTime: Instant? = null,
    @ColumnInfo(name = "actual_start_datetime")
    val actualStartDateTime: Instant? = null,
    @ColumnInfo(name = "actual_end_datetime")
    val actualEndDateTime: Instant? = null,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    val thumbnailUrl: String = "",
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",
    @ColumnInfo(name = "viewer_count", defaultValue = "null")
    val viewerCount: BigInteger? = null,
)

@Entity(
    tableName = "free_chat",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("video_id")],
)
internal class FreeChatTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo("is_free_chat", defaultValue = "null")
    val isFreeChat: Boolean? = null,
)

@Entity(
    tableName = "video_expire",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("video_id")],
)
internal class YouTubeVideoExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "expired_at", defaultValue = "null")
    val expiredAt: Instant? = null,
)

@Entity(tableName = "yt_video_is_archived")
internal class YouTubeVideoIsArchivedTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("video_id")
    val videoId: YouTubeVideo.Id, // archived video is not cached so not to be constrained by foreign key
    @ColumnInfo("is_archived")
    val isArchived: Boolean? = null,
)