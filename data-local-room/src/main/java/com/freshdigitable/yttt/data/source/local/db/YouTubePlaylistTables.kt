package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemDetail
import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems
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
)
internal class YouTubePlaylistItemTable(
    @PrimaryKey
    @ColumnInfo(name = "id")
    override val id: YouTubePlaylistItem.Id,
    @ColumnInfo(name = "playlist_id", index = true)
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo(name = "video_id")
    override val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "published_at")
    override val publishedAt: Instant,
) : YouTubePlaylistItem {
    constructor(item: YouTubePlaylistItem) : this(
        item.id,
        item.playlistId,
        item.videoId,
        item.publishedAt,
    )

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylistItems(items: Collection<YouTubePlaylistItemTable>)

        @Query("DELETE FROM playlist_item WHERE playlist_id = :id")
        suspend fun removePlaylistItemsByPlaylistId(id: YouTubePlaylist.Id)

        @Query("SELECT * FROM playlist_item AS s WHERE s.playlist_id = :id LIMIT :maxResult")
        suspend fun findPlaylistItemIds(
            id: YouTubePlaylist.Id,
            maxResult: Long,
        ): List<YouTubePlaylistItemTable>

        @Query("DELETE FROM playlist_item")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "playlist_item_addition",
    foreignKeys = [
        ForeignKey(
            entity = YouTubePlaylistItemTable::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
        ),
    ],
)
class YouTubePlaylistItemAdditionTable(
    @PrimaryKey
    @ColumnInfo(name = "item_id") val id: YouTubePlaylistItem.Id,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "channel_id") val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "video_owner_channel_id", defaultValue = "null")
    val videoOwnerChannelId: YouTubeChannel.Id? = null
) {
    constructor(item: YouTubePlaylistItemDetail) : this(
        item.id,
        item.title,
        item.channel.id,
        item.thumbnailUrl,
        item.description,
        item.videoOwnerChannelId,
    )

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addPlaylistItemAdditions(item: Collection<YouTubePlaylistItemAdditionTable>)

        @Query("DELETE FROM playlist_item_addition WHERE item_id IN (:id)")
        suspend fun removePlaylistItemsByPlaylistItemIds(id: Collection<YouTubePlaylistItem.Id>)

        @Query("DELETE FROM playlist_item_addition")
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

internal class YouTubePlaylistWithItemIdsDb(
    @Embedded
    override val playlist: YouTubePlaylistTable,
    @ColumnInfo(name = "etag")
    override val eTag: String?,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    override val items: List<YouTubePlaylistItemTable>,
) : YouTubePlaylistWithItems {
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

internal data class YouTubePlaylistItemDetailDb(
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
) : YouTubePlaylistItemDetail {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT p.*, a.title AS title, a.thumbnail_url AS thumbnail_url," +
                " a.video_owner_channel_id AS video_owner_channel_id, a.description AS description," +
                " c.id AS channel_id, c.title AS channel_title FROM playlist_item AS p " +
                "INNER JOIN playlist_item_addition AS a ON a.item_id = p.id " +
                "INNER JOIN channel AS c ON c.id = a.channel_id " +
                "WHERE p.playlist_id = :id"
        )
        suspend fun findPlaylistItemByPlaylistId(id: YouTubePlaylist.Id): List<YouTubePlaylistItemDetailDb>

        @Query("SELECT p.fetched_at AS fetched_at, p.max_age AS max_age FROM playlist_expire AS p WHERE p.playlist_id = :id")
        suspend fun findPlaylistItemCacheControlByPlaylistId(id: YouTubePlaylist.Id): CacheControlDb?
    }
}

internal interface YouTubePlaylistDaoProviders {
    val youTubePlaylistDao: YouTubePlaylistTable.Dao
    val youTubePlaylistExpireDao: YouTubePlaylistExpireTable.Dao
    val youTubePlaylistItemDao: YouTubePlaylistItemTable.Dao
    val youTubePlaylistItemAdditionDao: YouTubePlaylistItemAdditionTable.Dao
    val youTubePlaylistItemDbDao: YouTubePlaylistItemDetailDb.Dao
    val youTubePlaylistWithItemIdsDao: YouTubePlaylistWithItemIdsDb.Dao
    val youTubePlaylistWithItemsEtagDao: YouTubePlaylistWithItemsEtag.Dao
}

internal interface YouTubePlaylistDao : YouTubePlaylistTable.Dao, YouTubePlaylistExpireTable.Dao,
    YouTubePlaylistItemTable.Dao, YouTubePlaylistItemDetailDb.Dao,
    YouTubePlaylistItemAdditionTable.Dao,
    YouTubePlaylistWithItemIdsDb.Dao, YouTubePlaylistWithItemsEtag.Dao

internal class YouTubePlaylistDaoImpl @Inject constructor(
    private val db: YouTubePlaylistDaoProviders
) : YouTubePlaylistDao, YouTubePlaylistTable.Dao by db.youTubePlaylistDao,
    YouTubePlaylistExpireTable.Dao by db.youTubePlaylistExpireDao,
    YouTubePlaylistItemTable.Dao by db.youTubePlaylistItemDao,
    YouTubePlaylistItemAdditionTable.Dao by db.youTubePlaylistItemAdditionDao,
    YouTubePlaylistItemDetailDb.Dao by db.youTubePlaylistItemDbDao,
    YouTubePlaylistWithItemIdsDb.Dao by db.youTubePlaylistWithItemIdsDao,
    YouTubePlaylistWithItemsEtag.Dao by db.youTubePlaylistWithItemsEtagDao {
    override suspend fun deleteTable() {
        listOf(
            db.youTubePlaylistDao,
            db.youTubePlaylistExpireDao,
            db.youTubePlaylistItemDao,
            db.youTubePlaylistItemAdditionDao,
            db.youTubePlaylistWithItemsEtagDao,
        ).forEach { it.deleteTable() }
    }
}
