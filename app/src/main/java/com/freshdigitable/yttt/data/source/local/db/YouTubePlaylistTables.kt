package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEx
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.time.Duration
import java.time.Instant

@Entity(tableName = "playlist")
class YouTubePlaylistTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: YouTubePlaylist.Id,
    @ColumnInfo(name = "last_modified")
    val lastModified: Instant = Instant.now(),
    @ColumnInfo(name = "max_age")
    val maxAge: Duration = MAX_AGE_DEFAULT,
) {
    companion object {
        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        private val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        private val MAX_AGE_FOR_ACTIVE_ACCOUNT: Duration = Duration.ofMinutes(30)
        val RECENTLY_BOARDER: Duration = Duration.ofDays(3)

        fun getMaxAgeUpperLimit(isPublishedRecently: Boolean): Duration =
            if (isPublishedRecently) MAX_AGE_FOR_ACTIVE_ACCOUNT else MAX_AGE_MAX

        fun createWithMaxAge(id: YouTubePlaylist.Id): YouTubePlaylistTable =
            YouTubePlaylistTable(id, maxAge = MAX_AGE_MAX)
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
class YouTubePlaylistItemTable(
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

@DatabaseView(
    "SELECT p.*, c.icon AS channel_icon, c.title AS channel_title FROM playlist_item AS p" +
        " INNER JOIN channel AS c ON c.id = p.channel_id",
    viewName = "playlist_item_view",
)
data class YouTubePlaylistItemDb(
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

data class YouTubePlaylistDb(
    @Embedded
    val playlist: YouTubePlaylistTable,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    val playlistItems: List<YouTubePlaylistItemDb>,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    val archived: List<YouTubePlaylistItemIsArchivedDbView>,
) {
    val playlistItemsWithArchived: List<YouTubePlaylistItemEx>
        get() {
            val a = archived.associateBy { it.playlistItemId }
            return playlistItems.map { item ->
                YouTubePlaylistItemExDb(
                    item = item,
                    isArchived = a[item.id]?.isArchived,
                )
            }
        }
}

@DatabaseView(
    "SELECT i.playlist_id, i.id AS playlist_item_id, v.is_archived FROM playlist_item AS i " +
        "INNER JOIN yt_video_is_archived AS v ON i.video_id = v.video_id",
    viewName = "yt_playlist_item_is_archived",
)
class YouTubePlaylistItemIsArchivedDbView(
    @ColumnInfo("playlist_id")
    val playlistId: YouTubePlaylist.Id,
    @ColumnInfo("playlist_item_id")
    val playlistItemId: YouTubePlaylistItem.Id,
    @ColumnInfo("is_archived")
    val isArchived: Boolean?,
)

data class YouTubePlaylistItemExDb(
    override val isArchived: Boolean?,
    private val item: YouTubePlaylistItemDb,
) : YouTubePlaylistItemEx, YouTubePlaylistItem by item
