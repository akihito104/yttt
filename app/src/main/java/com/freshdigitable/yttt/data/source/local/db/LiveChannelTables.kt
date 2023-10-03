package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelAddition
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LiveVideo
import java.math.BigInteger
import java.time.Instant

@Entity(tableName = "channel")
data class LiveChannelTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: LiveChannel.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    override val title: String = "",
    @ColumnInfo(name = "icon", defaultValue = "")
    override val iconUrl: String = "",
) : LiveChannel

@Entity(
    tableName = "channel_addition",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["id"],
        ),
    ],
)
data class LiveChannelAdditionTable(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: LiveChannel.Id,
    @ColumnInfo(name = "banner_url")
    override val bannerUrl: String?,
    @ColumnInfo(name = "subscriber_count")
    override val subscriberCount: BigInteger,
    @ColumnInfo(name = "is_subscriber_hidden")
    override val isSubscriberHidden: Boolean,
    @ColumnInfo(name = "video_count")
    override val videoCount: BigInteger,
    @ColumnInfo(name = "view_count")
    override val viewsCount: BigInteger,
    @ColumnInfo(name = "published_at")
    override val publishedAt: Instant,
    @ColumnInfo(name = "custom_url")
    override val customUrl: String,
    @ColumnInfo(name = "keywords")
    val keywordsRaw: String,
    @ColumnInfo(name = "description")
    override val description: String?,
    @ColumnInfo(name = "uploaded_playlist_id")
    override val uploadedPlayList: LivePlaylist.Id?,
) : LiveChannelAddition {
    override val keywords: Collection<String>
        get() = keywordsRaw.split(",", " ")
}

@DatabaseView(
    "SELECT c.icon, c.title, a.* FROM channel AS c INNER JOIN channel_addition AS a ON c.id = a.id",
    viewName = "channel_detail",
)
data class LiveChannelDetailDbView(
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "icon")
    override val iconUrl: String,
    @Embedded
    val addition: LiveChannelAdditionTable,
) : LiveChannelDetail, LiveChannel, LiveChannelAddition by addition {
    @Ignore
    override val id: LiveChannel.Id = addition.id
}

@Entity(
    tableName = "channel_log",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
        ForeignKey(
            entity = LiveVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("channel_id"), Index("video_id")],
)
data class LiveChannelLogTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: LiveChannelLog.Id,
    @ColumnInfo(name = "datetime")
    override val dateTime: Instant,
    @ColumnInfo(name = "video_id")
    override val videoId: LiveVideo.Id,
    @ColumnInfo(name = "channel_id")
    override val channelId: LiveChannel.Id,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    override val thumbnailUrl: String = "",
) : LiveChannelLog
