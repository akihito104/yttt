package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
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
class YouTubeVideoTable(
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
class FreeChatTable(
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
class YouTubeVideoExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "expired_at", defaultValue = "null")
    val expiredAt: Instant? = null,
)

@DatabaseView(
    "SELECT v.id, v.title, v.channel_id, v.schedule_start_datetime, v.schedule_end_datetime, " +
        "v.actual_start_datetime, v.actual_end_datetime, v.thumbnail, v.description, v.viewer_count, " +
        "c.title AS channel_title, c.icon AS channel_icon, f.is_free_chat AS is_free_chat " +
        "FROM video AS v " +
        "INNER JOIN channel AS c ON c.id = v.channel_id " +
        "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id",
    viewName = "video_view",
)
data class YouTubeVideoDbView(
    @ColumnInfo(name = "id")
    override val id: YouTubeVideo.Id,
    @ColumnInfo(name = "title")
    override val title: String,
    @Embedded(prefix = "channel_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo(name = "schedule_start_datetime")
    override val scheduledStartDateTime: Instant?,
    @ColumnInfo(name = "schedule_end_datetime")
    override val scheduledEndDateTime: Instant?,
    @ColumnInfo(name = "actual_start_datetime")
    override val actualStartDateTime: Instant?,
    @ColumnInfo(name = "actual_end_datetime")
    override val actualEndDateTime: Instant?,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    override val thumbnailUrl: String = "",
    @ColumnInfo(name = "is_free_chat", defaultValue = "null")
    override val isFreeChat: Boolean? = null,
    @ColumnInfo(name = "description", defaultValue = "")
    override val description: String = "",
    @ColumnInfo(name = "viewer_count", defaultValue = "null")
    override val viewerCount: BigInteger? = null,
) : YouTubeVideo
