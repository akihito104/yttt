package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelAddition
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.math.BigInteger
import java.time.Instant

@Entity(tableName = "channel")
data class YouTubeChannelTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: YouTubeChannel.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    override val title: String = "",
    @ColumnInfo(name = "icon", defaultValue = "")
    override val iconUrl: String = "",
) : YouTubeChannel

@Entity(
    tableName = "channel_addition",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["id"],
        ),
        ForeignKey(
            entity = YouTubePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["uploaded_playlist_id"],
        ),
    ],
)
data class YouTubeChannelAdditionTable(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: YouTubeChannel.Id,
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
    @ColumnInfo(name = "uploaded_playlist_id", index = true)
    override val uploadedPlayList: YouTubePlaylist.Id?,
) : YouTubeChannelAddition {
    override val keywords: Collection<String>
        get() = keywordsRaw.split(",", " ")
}

data class YouTubeChannelDetailDb(
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "icon")
    override val iconUrl: String,
    @Embedded
    val addition: YouTubeChannelAdditionTable,
) : YouTubeChannelDetail, YouTubeChannel, YouTubeChannelAddition by addition {
    @Ignore
    override val id: YouTubeChannel.Id = addition.id
}

@Entity(
    tableName = "channel_log",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
        ForeignKey(
            entity = YouTubeVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("channel_id"), Index("video_id")],
)
data class YouTubeChannelLogTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: YouTubeChannelLog.Id,
    @ColumnInfo(name = "datetime")
    override val dateTime: Instant,
    @ColumnInfo(name = "video_id")
    override val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "channel_id")
    override val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    override val thumbnailUrl: String = "",
) : YouTubeChannelLog
