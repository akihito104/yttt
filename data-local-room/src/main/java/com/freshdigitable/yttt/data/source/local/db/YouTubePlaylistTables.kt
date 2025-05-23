package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubePlaylistUpdatable
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemSummaries
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Entity(tableName = "playlist")
internal class YouTubePlaylistTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: YouTubePlaylist.Id,
    @ColumnInfo(name = "last_modified")
    override val fetchedAt: Instant = Instant.EPOCH,
    @ColumnInfo(name = "max_age")
    override val maxAge: Duration = MAX_AGE_DEFAULT,
) : YouTubePlaylistUpdatable {
    @Ignore
    override val thumbnailUrl: String = "" // TODO: implement for all_playlist with paging

    @Ignore
    override val title: String = "" // TODO

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylist(playlist: YouTubePlaylistTable)

        @Upsert
        suspend fun addPlaylists(playlist: Collection<YouTubePlaylistTable>)

        @Query("SELECT * FROM playlist WHERE id = :id")
        suspend fun findPlaylistById(id: YouTubePlaylist.Id): YouTubePlaylistTable?

        @Query("SELECT * FROM playlist WHERE id IN (:id)")
        suspend fun findPlaylistsById(id: Collection<YouTubePlaylist.Id>): List<YouTubePlaylistTable>

        @Query("UPDATE playlist SET last_modified = :lastModified, max_age = :maxAge WHERE id = :id")
        suspend fun updatePlaylist(id: YouTubePlaylist.Id, lastModified: Instant, maxAge: Duration)

        @Query("DELETE FROM playlist")
        override suspend fun deleteTable()
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
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylistItems(items: Collection<YouTubePlaylistItemTable>)

        @Query("DELETE FROM playlist_item WHERE playlist_id = :id")
        suspend fun removePlaylistItemsByPlaylistId(id: YouTubePlaylist.Id)

        @Query("DELETE FROM playlist_item")
        override suspend fun deleteTable()
    }
}

@DatabaseView(
    "SELECT i.playlist_id, i.id AS playlist_item_id, i.video_id, v.is_archived, e.expired_at AS video_expired_at " +
        "FROM playlist_item AS i " +
        "LEFT OUTER JOIN yt_video_is_archived AS v ON i.video_id = v.video_id " +
        "LEFT OUTER JOIN video_expire AS e ON i.video_id = e.video_id",
    viewName = "yt_playlist_item_summary",
)
internal class YouTubePlaylistItemSummaryDb(
    @ColumnInfo("playlist_id")
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo("playlist_item_id")
    override val playlistItemId: YouTubePlaylistItem.Id,
    @ColumnInfo("video_id")
    override val videoId: YouTubeVideo.Id,
    @ColumnInfo("is_archived")
    override val isArchived: Boolean?,
    @ColumnInfo("video_expired_at")
    override val videoExpiredAt: Instant?,
) : YouTubePlaylistItemSummary {
    @androidx.room.Dao
    internal interface Dao {
        @Query("SELECT * FROM yt_playlist_item_summary AS s WHERE s.playlist_id = :id LIMIT :maxResult")
        suspend fun findPlaylistItemSummary(
            id: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemSummaryDb>
    }
}

internal class YouTubePlaylistWithItemSummariesDb(
    @Embedded
    override val playlist: YouTubePlaylistTable,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    override val summary: List<YouTubePlaylistItemSummaryDb>,
) : YouTubePlaylistWithItemSummaries, Updatable by playlist {
    @androidx.room.Dao
    interface Dao {
        @Transaction
        @Query("SELECT * FROM playlist WHERE id = :id")
        suspend fun findPlaylistWithItemSummaries(id: YouTubePlaylist.Id): YouTubePlaylistWithItemSummariesDb?
    }
}

internal data class YouTubePlaylistItemDb(
    @ColumnInfo(name = "id")
    override val id: YouTubePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id")
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo(name = "title")
    override val title: String,
    @Embedded(prefix = "channel_")
    override val channel: YouTubeChannelTitleDb,
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
) : YouTubePlaylistItem {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT p.*, c.id AS channel_id, c.title AS channel_title FROM playlist_item AS p " +
                "INNER JOIN channel AS c ON c.id = p.channel_id WHERE p.playlist_id = :id"
        )
        suspend fun findPlaylistItemByPlaylistId(id: YouTubePlaylist.Id): List<YouTubePlaylistItemDb>
    }
}

internal interface YouTubePlaylistDaoProviders {
    val youTubePlaylistDao: YouTubePlaylistTable.Dao
    val youTubePlaylistItemDao: YouTubePlaylistItemTable.Dao
    val youTubePlaylistItemSummaryDbDao: YouTubePlaylistItemSummaryDb.Dao
    val youTubePlaylistItemDbDao: YouTubePlaylistItemDb.Dao
    val youTubePlaylistWithItemSummariesDbDao: YouTubePlaylistWithItemSummariesDb.Dao
}

internal interface YouTubePlaylistDao : YouTubePlaylistTable.Dao, YouTubePlaylistItemTable.Dao,
    YouTubePlaylistItemSummaryDb.Dao, YouTubePlaylistItemDb.Dao,
    YouTubePlaylistWithItemSummariesDb.Dao

internal class YouTubePlaylistDaoImpl @Inject constructor(
    private val db: YouTubePlaylistDaoProviders
) : YouTubePlaylistDao, YouTubePlaylistTable.Dao by db.youTubePlaylistDao,
    YouTubePlaylistItemTable.Dao by db.youTubePlaylistItemDao,
    YouTubePlaylistItemSummaryDb.Dao by db.youTubePlaylistItemSummaryDbDao,
    YouTubePlaylistItemDb.Dao by db.youTubePlaylistItemDbDao,
    YouTubePlaylistWithItemSummariesDb.Dao by db.youTubePlaylistWithItemSummariesDbDao {
    override suspend fun deleteTable() {
        listOf(
            db.youTubePlaylistDao,
            db.youTubePlaylistItemDao
        ).forEach { it.deleteTable() }
    }
}
