package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LivePlaylistItem
import com.freshdigitable.yttt.data.model.LiveVideo
import java.time.Duration
import java.time.Instant

@Entity(
    tableName = "playlist",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"]
        ),
    ],
)
class LivePlaylistTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: LivePlaylist.Id,
    @ColumnInfo(name = "channel_id", index = true)
    val channelId: LiveChannel.Id,
    @ColumnInfo(name = "last_modified")
    val lastModified: Instant = Instant.now(),
    @ColumnInfo(name = "max_age")
    val maxAge: Duration = MAX_AGE_DEFAULT,
) {
    companion object {
        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        val MAX_AGE_MAX: Duration = Duration.ofDays(1)
    }
}

@Entity(
    tableName = "playlist_item",
    foreignKeys = [
        ForeignKey(
            entity = LivePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
        ),
    ]
)
class LivePlaylistItemTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: LivePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id", index = true)
    val playlistId: LivePlaylist.Id,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "channel_id")
    val channelId: LiveChannel.Id,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String,
    @ColumnInfo(name = "video_id")
    val videoId: LiveVideo.Id,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "video_owner_channel_id", defaultValue = "null")
    val videoOwnerChannelId: LiveChannel.Id? = null,
    @ColumnInfo(name = "published_at")
    val publishedAt: Instant,
)

@DatabaseView(
    "SELECT p.*, c.icon AS channel_icon, c.title AS channel_title FROM playlist_item AS p" +
        " INNER JOIN channel AS c ON c.id = p.channel_id",
    viewName = "playlist_item_view",
)
data class LivePlaylistItemDb(
    @ColumnInfo(name = "id")
    override val id: LivePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id")
    override val playlistId: LivePlaylist.Id,
    @ColumnInfo(name = "title")
    override val title: String,
    @Embedded(prefix = "channel_")
    override val channel: LiveChannelTable,
    @ColumnInfo(name = "thumbnail_url")
    override val thumbnailUrl: String,
    @ColumnInfo(name = "video_id")
    override val videoId: LiveVideo.Id,
    @ColumnInfo(name = "description")
    override val description: String,
    @ColumnInfo(name = "video_owner_channel_id")
    override val videoOwnerChannelId: LiveChannel.Id?,
    @ColumnInfo(name = "published_at")
    override val publishedAt: Instant,
) : LivePlaylistItem

fun LivePlaylistItem.toDbEntity(): LivePlaylistItemTable = LivePlaylistItemTable(
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

data class LivePlaylistDb(
    @Embedded
    val playlist: LivePlaylistTable,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id"
    )
    val playlistItems: List<LivePlaylistItemDb>,
)
