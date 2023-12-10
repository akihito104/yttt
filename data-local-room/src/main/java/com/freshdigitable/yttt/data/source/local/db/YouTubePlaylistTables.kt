package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.time.Duration
import java.time.Instant

@Entity(tableName = "playlist")
internal class YouTubePlaylistTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: YouTubePlaylist.Id,
    @ColumnInfo(name = "last_modified")
    val lastModified: Instant = Instant.EPOCH,
    @ColumnInfo(name = "max_age")
    val maxAge: Duration = MAX_AGE_DEFAULT,
) : YouTubePlaylist {
    @Ignore
    override val thumbnailUrl: String = "" // TODO

    @Ignore
    override val title: String = "" // TODO

    companion object {
        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        private val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        private val MAX_AGE_FOR_ACTIVE_ACCOUNT: Duration = Duration.ofMinutes(30)
        val RECENTLY_BOARDER: Duration = Duration.ofDays(3)

        fun getMaxAgeUpperLimit(isPublishedRecently: Boolean): Duration =
            if (isPublishedRecently) MAX_AGE_FOR_ACTIVE_ACCOUNT else MAX_AGE_MAX

        fun createWithMaxAge(id: YouTubePlaylist.Id, lastModified: Instant): YouTubePlaylistTable =
            YouTubePlaylistTable(id, lastModified, maxAge = MAX_AGE_MAX)
    }
}

@Entity(
    tableName = "playlist_item",
    foreignKeys = [
        ForeignKey(
            entity = YouTubePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
        ),
    ],
    primaryKeys = ["id", "playlist_id"],
    indices = [
        Index(
            value = ["playlist_id", "id"],
            name = "index_yt_playlist_item",
            unique = true,
        ),
    ],
)
internal class YouTubePlaylistItemTable(
    @ColumnInfo(name = "id")
    val id: YouTubePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id", index = true)
    val playlistId: YouTubePlaylist.Id,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String,
    @ColumnInfo(name = "video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "video_owner_channel_id", defaultValue = "null")
    val videoOwnerChannelId: YouTubeChannel.Id? = null,
    @ColumnInfo(name = "published_at")
    val publishedAt: Instant,
)

internal data class YouTubePlaylistItemDb(
    @ColumnInfo(name = "id")
    override val id: YouTubePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id")
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo(name = "title")
    override val title: String,
    @Embedded(prefix = "channel_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo(name = "thumbnail_url")
    override val thumbnailUrl: String,
    @ColumnInfo(name = "video_id")
    override val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "description")
    override val description: String,
    @ColumnInfo(name = "video_owner_channel_id")
    override val videoOwnerChannelId: YouTubeChannel.Id?,
    @ColumnInfo(name = "published_at")
    override val publishedAt: Instant,
) : YouTubePlaylistItem
