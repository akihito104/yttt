package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelAddition
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelRelatedPlaylist
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject

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
        @Upsert
        suspend fun addChannels(channels: Collection<YouTubeChannelTable>)

        @Query("SELECT * FROM channel WHERE id = :id")
        suspend fun findChannel(id: YouTubeChannel.Id): YouTubeChannelTable?

        @Query("SELECT * FROM channel WHERE id IN (:id)")
        suspend fun findChannels(id: Set<YouTubeChannel.Id>): List<YouTubeChannelTable>

        @Query("SELECT id FROM channel WHERE id NOT IN (SELECT channel_id FROM subscription)")
        suspend fun findUnsubscribedChannelIds(): List<YouTubeChannel.Id>

        @Query("DELETE FROM channel WHERE id IN (:ids)")
        suspend fun removeChannels(ids: Set<YouTubeChannel.Id>)

        @Query("DELETE FROM channel")
        override suspend fun deleteTable()
    }
}

internal class YouTubeChannelTitleDb(
    @ColumnInfo(name = "id")
    override val id: YouTubeChannel.Id,
    @ColumnInfo(name = "title")
    override val title: String,
) : YouTubeChannelTitle

@Entity(
    tableName = "channel_addition",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["id"],
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
) : YouTubeChannelAddition {
    override val keywords: Collection<String>
        get() = keywordsRaw.split(",", " ")

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelAddition(addition: Collection<YouTubeChannelAdditionTable>)

        @Query("DELETE FROM channel_addition WHERE id IN (:ids)")
        suspend fun removeChannelAddition(ids: Set<YouTubeChannel.Id>)

        @Query("DELETE FROM channel_addition")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "yt_channel_related_playlist",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
        ForeignKey(
            entity = YouTubePlaylistTable::class,
            parentColumns = ["id"],
            childColumns = ["uploaded_playlist_id"],
        ),
    ],
)
internal class YouTubeChannelRelatedPlaylistTable(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    override val id: YouTubeChannel.Id,
    @ColumnInfo(
        name = "uploaded_playlist_id",
        index = true,
    )
    override val uploadedPlayList: YouTubePlaylist.Id?,
) : YouTubeChannelRelatedPlaylist {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelRelatedPlaylists(entities: Collection<YouTubeChannelRelatedPlaylistTable>)

        @Query("SELECT * FROM yt_channel_related_playlist WHERE channel_id IN (:ids)")
        suspend fun findChannelRelatedPlaylists(ids: Set<YouTubeChannel.Id>): List<YouTubeChannelRelatedPlaylistTable>

        @Query("DELETE FROM yt_channel_related_playlist WHERE channel_id IN (:ids)")
        suspend fun removeChannelRelatedPlaylists(ids: Set<YouTubeChannel.Id>)

        @Query("DELETE FROM yt_channel_related_playlist")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "channel_addition_expire",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
internal data class YouTubeChannelAdditionExpireTable(
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @Embedded
    val cacheControl: CacheControlDb,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelAdditionExpire(entities: Collection<YouTubeChannelAdditionExpireTable>)

        @Query("DELETE FROM channel_addition_expire WHERE channel_id IN (:ids)")
        suspend fun removeChannelAdditionExpire(ids: Set<YouTubeChannel.Id>)

        @Query("DELETE FROM channel_addition_expire")
        override suspend fun deleteTable()
    }
}

internal data class YouTubeChannelDetailDb(
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "icon")
    override val iconUrl: String,
    @Embedded
    val addition: YouTubeChannelAdditionTable,
    @ColumnInfo(name = "uploaded_playlist_id")
    override val uploadedPlayList: YouTubePlaylist.Id?,
) : YouTubeChannelDetail, YouTubeChannelAddition by addition {
    @get:Ignore
    override val id: YouTubeChannel.Id get() = addition.id

    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT c.icon AS icon, c.title AS title, a.*, e.fetched_at AS fetched_at, e.max_age AS max_age, " +
                " p.uploaded_playlist_id AS uploaded_playlist_id FROM channel AS c " +
                "INNER JOIN channel_addition AS a ON c.id = a.id " +
                "INNER JOIN channel_addition_expire AS e ON c.id = e.channel_id " +
                "LEFT OUTER JOIN yt_channel_related_playlist AS p ON c.id = p.channel_id " +
                "WHERE c.id IN (:id)"
        )
        suspend fun findChannelDetail(id: Collection<YouTubeChannel.Id>): List<UpdatableYouTubeChannelDetailDb>
    }
}

internal data class UpdatableYouTubeChannelDetailDb(
    @Embedded override val item: YouTubeChannelDetailDb,
    @Embedded override val cacheControl: CacheControlDb,
) : Updatable<YouTubeChannelDetail>

@Entity(
    tableName = "channel_log",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
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
    @Ignore
    override val title: String = "" // TODO

    @Ignore
    override val type: String = "" // TODO

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelLogs(logs: Collection<YouTubeChannelLogTable>)

        @Query(
            "SELECT * FROM channel_log" +
                " WHERE channel_id = :channelId AND datetime >= :publishedAfter" +
                " ORDER BY datetime DESC LIMIT :maxResult"
        )
        suspend fun findChannelLogs(
            channelId: YouTubeChannel.Id,
            publishedAfter: Instant,
            maxResult: Long? = Long.MAX_VALUE,
        ): List<YouTubeChannelLogTable>

        @Query(
            "SELECT * FROM channel_log WHERE channel_id = :channelId" +
                " ORDER BY datetime DESC LIMIT :maxResult"
        )
        suspend fun findChannelLogs(
            channelId: YouTubeChannel.Id,
            maxResult: Long? = Long.MAX_VALUE,
        ): List<YouTubeChannelLogTable>

        @Query("DELETE FROM channel_log WHERE channel_id IN (:ids)")
        suspend fun removeChannelLogsByChannelId(ids: Set<YouTubeChannel.Id>)

        @Query("DELETE FROM channel_log")
        override suspend fun deleteTable()
    }
}

internal interface YouTubeChannelDaoProviders {
    val youTubeChannelDao: YouTubeChannelTable.Dao
    val youTubeChannelAdditionDao: YouTubeChannelAdditionTable.Dao
    val youTubeChannelDetailDbDao: YouTubeChannelDetailDb.Dao
    val youTubeChannelRelatedPlaylistDbDao: YouTubeChannelRelatedPlaylistTable.Dao
    val youTubeChannelLogDao: YouTubeChannelLogTable.Dao
    val youTubeChannelAdditionExpireDao: YouTubeChannelAdditionExpireTable.Dao
}

internal interface YouTubeChannelDao : YouTubeChannelTable.Dao, YouTubeChannelLogTable.Dao,
    YouTubeChannelDetailDb.Dao, YouTubeChannelRelatedPlaylistTable.Dao {
    suspend fun addChannelEntities(channels: Collection<YouTubeChannel>)
    suspend fun addChannelDetails(channelDetail: Collection<Updatable<YouTubeChannelDetail>>)
    suspend fun addChannelRelatedPlaylistEntities(entities: Collection<YouTubeChannelRelatedPlaylist>)
    suspend fun addChannelLogEntities(logs: Collection<YouTubeChannelLog>)
    suspend fun removeChannelEntities(id: Set<YouTubeChannel.Id>)
}

internal class YouTubeChannelDaoImpl @Inject constructor(
    private val db: AppDatabase,
) : YouTubeChannelDao, YouTubeChannelTable.Dao by db.youTubeChannelDao,
    YouTubeChannelLogTable.Dao by db.youTubeChannelLogDao,
    YouTubeChannelDetailDb.Dao by db.youTubeChannelDetailDbDao,
    YouTubeChannelRelatedPlaylistTable.Dao by db.youTubeChannelRelatedPlaylistDbDao {
    override suspend fun addChannelEntities(channels: Collection<YouTubeChannel>) {
        val entities = channels.map { it.toDbEntity() }
        addChannels(entities)
    }

    override suspend fun addChannelDetails(
        channelDetail: Collection<Updatable<YouTubeChannelDetail>>,
    ) = db.withTransaction {
        val additions = channelDetail.map { it.item.toAddition() }
        val expired = channelDetail
            .map { YouTubeChannelAdditionExpireTable(it.item.id, it.cacheControl.toDb()) }
        addChannelEntities(channelDetail.map { it.item as YouTubeChannel })
        db.youTubeChannelAdditionDao.addChannelAddition(additions)
        db.youTubeChannelAdditionExpireDao.addChannelAdditionExpire(expired)
    }

    override suspend fun addChannelRelatedPlaylistEntities(entities: Collection<YouTubeChannelRelatedPlaylist>) {
        val e = entities.mapNotNull { c ->
            c.uploadedPlayList?.let { YouTubeChannelRelatedPlaylistTable(c.id, it) }
        }
        addChannelRelatedPlaylists(e)
    }

    override suspend fun addChannelLogEntities(logs: Collection<YouTubeChannelLog>) =
        db.withTransaction {
            val channels = logs.map { it.channelId }.distinct()
                .filter { findChannel(it) == null }
                .map { YouTubeChannelTable(id = it) }
            addChannels(channels)
            addChannelLogs(logs.filter { it.videoId != null }.map { it.toDbEntity() })
        }

    override suspend fun removeChannelEntities(id: Set<YouTubeChannel.Id>) = db.withTransaction {
        removeChannelLogsByChannelId(id)
        db.youTubeChannelAdditionExpireDao.removeChannelAdditionExpire(id)
        removeChannelRelatedPlaylists(id)
        db.youTubeChannelAdditionDao.removeChannelAddition(id)
        removeChannels(id)
    }

    override suspend fun deleteTable() {
        listOf(
            db.youTubeChannelDao,
            db.youTubeChannelAdditionDao,
            db.youTubeChannelLogDao,
            db.youTubeChannelRelatedPlaylistDbDao,
            db.youTubeChannelAdditionExpireDao,
        ).forEach { it.deleteTable() }
    }

    companion object {
        private fun YouTubeChannel.toDbEntity(): YouTubeChannelTable = YouTubeChannelTable(
            id = id, title = title, iconUrl = iconUrl,
        )

        private fun YouTubeChannelDetail.toAddition(): YouTubeChannelAdditionTable =
            YouTubeChannelAdditionTable(
                id = id,
                bannerUrl = bannerUrl,
                description = description,
                customUrl = customUrl,
                isSubscriberHidden = isSubscriberHidden,
                keywordsRaw = keywords.joinToString(","),
                publishedAt = publishedAt,
                subscriberCount = subscriberCount,
                videoCount = videoCount,
                viewsCount = viewsCount,
            )

        private fun YouTubeChannelLog.toDbEntity(): YouTubeChannelLogTable = YouTubeChannelLogTable(
            id = id,
            dateTime = dateTime,
            videoId = checkNotNull(videoId),
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
        )
    }
}
