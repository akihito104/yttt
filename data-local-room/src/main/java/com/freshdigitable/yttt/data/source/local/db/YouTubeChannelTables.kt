package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelAddition
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.math.BigInteger
import java.time.Instant

@Entity(tableName = "channel")
internal data class YouTubeChannelTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: YouTubeChannel.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    override val title: String = "",
    @ColumnInfo(name = "icon", defaultValue = "")
    override val iconUrl: String = "",
) : YouTubeChannel {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Query("DELETE FROM channel")
        override suspend fun deleteTable()
        interface Provider {
            val youTubeChannelDao: Dao
        }
    }
}

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
internal data class YouTubeChannelAdditionTable(
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

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Query("DELETE FROM channel_addition")
        override suspend fun deleteTable()
        interface Provider {
            val youTubeChannelAdditionDao: Dao
        }
    }
}

internal data class YouTubeChannelDetailDb(
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
internal data class YouTubeChannelLogTable(
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
) : YouTubeChannelLog {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Query("DELETE FROM channel_log")
        override suspend fun deleteTable()
        interface Provider {
            val youTubeChannelLogDao: Dao
        }
    }
}

internal interface YouTubeChannelDaoProviders : YouTubeChannelTable.Dao.Provider,
    YouTubeChannelAdditionTable.Dao.Provider, YouTubeChannelLogTable.Dao.Provider
