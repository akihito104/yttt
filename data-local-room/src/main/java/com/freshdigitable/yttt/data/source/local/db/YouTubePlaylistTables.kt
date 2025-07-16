package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
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
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemIds
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItemIds
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.time.Instant
import javax.inject.Inject

@Entity(tableName = "playlist")
internal class YouTubePlaylistTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id") override val id: YouTubePlaylist.Id,
    @ColumnInfo(name = "title", defaultValue = "") override val title: String = "",
    @ColumnInfo(name = "thumbnail_url", defaultValue = "") override val thumbnailUrl: String = "",
) : YouTubePlaylist {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylist(playlist: YouTubePlaylistTable)

        @Upsert
        suspend fun addPlaylists(playlist: Collection<YouTubePlaylistTable>)

        @Query("SELECT * FROM playlist WHERE id = :id")
        suspend fun findPlaylistById(id: YouTubePlaylist.Id): YouTubePlaylistTable?

        @Query("DELETE FROM playlist")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "playlist_expire",
    foreignKeys = [
        ForeignKey(
            entity = YouTubePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
        ),
    ],
)
internal class YouTubePlaylistExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "playlist_id") val id: YouTubePlaylist.Id,
    @Embedded val cacheControl: CacheControlDb,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylistExpire(playlist: YouTubePlaylistExpireTable)

        @Query(
            "SELECT p.*, e.fetched_at AS fetched_at, e.max_age AS max_age, t.etag AS etag FROM playlist AS p " +
                "LEFT OUTER JOIN playlist_expire AS e ON p.id = e.playlist_id " +
                "LEFT OUTER JOIN playlist_with_items_etag AS t ON p.id = t.playlist_id " +
                "WHERE p.id = :id"
        )
        suspend fun findUpdatablePlaylistById(id: YouTubePlaylist.Id): YouTubePlaylistUpdatableDb?

        @Query("DELETE FROM playlist_expire")
        override suspend fun deleteTable()
    }
}

internal class YouTubePlaylistUpdatableDb(
    @Embedded override val item: YouTubePlaylistTable,
    @Embedded override val cacheControl: CacheControlDb,
    @ColumnInfo(name = "etag") override val eTag: String?,
) : Updatable<YouTubePlaylist>

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

@Entity(
    tableName = "playlist_with_items_etag",
    foreignKeys = [
        ForeignKey(
            entity = YouTubePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
        ),
    ],
)
internal class YouTubePlaylistWithItemsEtag(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id") val playlistId: YouTubePlaylist.Id,
    @ColumnInfo(name = "etag") val eTag: String,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylistWithItemsEtag(etag: YouTubePlaylistWithItemsEtag)

        @Query("DELETE FROM playlist_with_items_etag")
        override suspend fun deleteTable()
    }
}

@DatabaseView(
    "SELECT i.playlist_id, i.id AS playlist_item_id, i.video_id FROM playlist_item AS i",
    viewName = "yt_playlist_item_summary",
)
internal class YouTubePlaylistItemIdDb(
    @ColumnInfo("playlist_id")
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo("playlist_item_id")
    override val id: YouTubePlaylistItem.Id,
    @ColumnInfo("video_id")
    override val videoId: YouTubeVideo.Id,
) : YouTubePlaylistItemIds {
    @androidx.room.Dao
    internal interface Dao {
        @Query("SELECT * FROM yt_playlist_item_summary AS s WHERE s.playlist_id = :id LIMIT :maxResult")
        suspend fun findPlaylistItemIds(
            id: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemIdDb>
    }
}

internal class YouTubePlaylistWithItemIdsDb(
    @Embedded
    override val playlist: YouTubePlaylistTable,
    @ColumnInfo(name = "etag")
    override val eTag: String?,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    override val items: List<YouTubePlaylistItemIdDb>,
) : YouTubePlaylistWithItemIds {
    @androidx.room.Dao
    internal interface Dao {
        @Transaction
        @Query(
            "SELECT p.*, e.etag AS etag FROM playlist AS p " +
                "LEFT OUTER JOIN playlist_with_items_etag AS e ON p.id = e.playlist_id WHERE id = :id"
        )
        suspend fun findPlaylistWithItemIds(id: YouTubePlaylist.Id): YouTubePlaylistWithItemIdsDb?
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
                "INNER JOIN channel AS c ON c.id = p.channel_id " +
                "WHERE p.playlist_id = :id"
        )
        suspend fun findPlaylistItemByPlaylistId(id: YouTubePlaylist.Id): List<YouTubePlaylistItemDb>

        @Query("SELECT p.fetched_at AS fetched_at, p.max_age AS max_age FROM playlist_expire AS p WHERE p.playlist_id = :id")
        suspend fun findPlaylistItemCacheControlByPlaylistId(id: YouTubePlaylist.Id): CacheControlDb?
    }
}

internal interface YouTubePlaylistDaoProviders {
    val youTubePlaylistDao: YouTubePlaylistTable.Dao
    val youTubePlaylistExpireDao: YouTubePlaylistExpireTable.Dao
    val youTubePlaylistItemDao: YouTubePlaylistItemTable.Dao
    val youTubePlaylistItemIdDao: YouTubePlaylistItemIdDb.Dao
    val youTubePlaylistItemDbDao: YouTubePlaylistItemDb.Dao
    val youTubePlaylistWithItemIdsDao: YouTubePlaylistWithItemIdsDb.Dao
    val youTubePlaylistWithItemsEtagDao: YouTubePlaylistWithItemsEtag.Dao
}

internal interface YouTubePlaylistDao : YouTubePlaylistTable.Dao, YouTubePlaylistExpireTable.Dao,
    YouTubePlaylistItemTable.Dao, YouTubePlaylistItemIdDb.Dao, YouTubePlaylistItemDb.Dao,
    YouTubePlaylistWithItemIdsDb.Dao, YouTubePlaylistWithItemsEtag.Dao

internal class YouTubePlaylistDaoImpl @Inject constructor(
    private val db: YouTubePlaylistDaoProviders
) : YouTubePlaylistDao, YouTubePlaylistTable.Dao by db.youTubePlaylistDao,
    YouTubePlaylistExpireTable.Dao by db.youTubePlaylistExpireDao,
    YouTubePlaylistItemTable.Dao by db.youTubePlaylistItemDao,
    YouTubePlaylistItemIdDb.Dao by db.youTubePlaylistItemIdDao,
    YouTubePlaylistItemDb.Dao by db.youTubePlaylistItemDbDao,
    YouTubePlaylistWithItemIdsDb.Dao by db.youTubePlaylistWithItemIdsDao,
    YouTubePlaylistWithItemsEtag.Dao by db.youTubePlaylistWithItemsEtagDao {
    override suspend fun deleteTable() {
        listOf(
            db.youTubePlaylistDao,
            db.youTubePlaylistExpireDao,
            db.youTubePlaylistItemDao,
            db.youTubePlaylistWithItemsEtagDao,
        ).forEach { it.deleteTable() }
    }
}
