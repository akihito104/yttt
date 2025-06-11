package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import com.freshdigitable.yttt.data.source.local.TableDeletable
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Entity(
    tableName = "video",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
internal class YouTubeVideoTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: YouTubeVideo.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    val title: String = "",
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "schedule_start_datetime")
    val scheduledStartDateTime: Instant? = null,
    @ColumnInfo(name = "schedule_end_datetime")
    val scheduledEndDateTime: Instant? = null,
    @ColumnInfo(name = "actual_start_datetime")
    val actualStartDateTime: Instant? = null,
    @ColumnInfo(name = "actual_end_datetime")
    val actualEndDateTime: Instant? = null,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    val thumbnailUrl: String = "",
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",
    @ColumnInfo(name = "viewer_count", defaultValue = "null")
    val viewerCount: BigInteger? = null,
    @ColumnInfo(name = "broadcast_content", defaultValue = "null")
    val broadcastContent: YouTubeVideo.BroadcastType? = null,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addVideoEntities(videos: Collection<YouTubeVideoTable>)

        @Query(
            "SELECT id FROM (SELECT id FROM video WHERE broadcast_content IS 'none') AS v " +
                "WHERE NOT EXISTS (SELECT video_id FROM playlist_item AS p WHERE v.id = p.video_id" +
                " UNION SELECT video_id FROM (SELECT video_id FROM free_chat WHERE is_free_chat IS 1) AS f WHERE v.id = f.video_id)"
        )
        suspend fun findUnusedVideoIds(): List<YouTubeVideo.Id>

        @Query("SELECT thumbnail FROM video WHERE id IN (:ids)")
        suspend fun findThumbnailUrlByIds(ids: Collection<YouTubeVideo.Id>): List<String>

        @Query("DELETE FROM video WHERE id IN (:videoIds)")
        suspend fun removeVideoEntities(videoIds: Collection<YouTubeVideo.Id>)

        @Query("SELECT id FROM video WHERE broadcast_content = 'none'")
        suspend fun findAllArchivedVideos(): List<YouTubeVideo.Id>

        @Query("DELETE FROM video")
        override suspend fun deleteTable()
    }

    override fun toString(): String = id.toString()
}

internal data class YouTubeVideoDb(
    @Embedded
    private val video: YouTubeVideoTable,
    @Embedded("c_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo("is_free_chat")
    override val isFreeChat: Boolean?,
    @ColumnInfo(name = "fetched_at")
    private val _fetchedAt: Instant?,
    @ColumnInfo(name = "max_age")
    private val _maxAge: Duration?,
) : YouTubeVideoExtended {
    override val id: YouTubeVideo.Id
        get() = video.id
    override val title: String
        get() = video.title
    override val thumbnailUrl: String
        get() = video.thumbnailUrl
    override val scheduledStartDateTime: Instant?
        get() = video.scheduledStartDateTime
    override val scheduledEndDateTime: Instant?
        get() = video.scheduledEndDateTime
    override val actualStartDateTime: Instant?
        get() = video.actualStartDateTime
    override val actualEndDateTime: Instant?
        get() = video.actualEndDateTime
    override val description: String
        get() = video.description
    override val viewerCount: BigInteger?
        get() = video.viewerCount
    override val liveBroadcastContent: YouTubeVideo.BroadcastType?
        get() = video.broadcastContent
    override val fetchedAt: Instant
        get() = _fetchedAt ?: Instant.EPOCH
    override val maxAge: Duration
        get() = _maxAge ?: Duration.ZERO

    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT v.*, c.id AS c_id, c.icon AS c_icon, c.title AS c_title, f.is_free_chat AS is_free_chat," +
                " e.fetched_at AS fetched_at, e.max_age AS max_age FROM video AS v " +
                "LEFT OUTER JOIN video_expire AS e ON e.video_id = v.id " +
                "INNER JOIN channel AS c ON c.id = v.channel_id " +
                "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id " +
                "WHERE v.id IN (:ids)"
        )
        suspend fun findVideosById(ids: Collection<YouTubeVideo.Id>): List<YouTubeVideoDb>

        @Query(
            "SELECT v.*, c.id AS c_id, c.icon AS c_icon, c.title AS c_title, f.is_free_chat AS is_free_chat," +
                " e.fetched_at AS fetched_at, e.max_age AS max_age FROM video AS v " +
                "LEFT OUTER JOIN video_expire AS e ON e.video_id = v.id " +
                "INNER JOIN channel AS c ON c.id = v.channel_id " +
                "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id " +
                "WHERE broadcast_content IS NOT 'none'"
        )
        fun watchAllUnfinishedVideos(): Flow<List<YouTubeVideoDb>>
    }
}

@Entity(
    tableName = "free_chat",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("video_id")],
)
internal class FreeChatTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo("is_free_chat", defaultValue = "null")
    val isFreeChat: Boolean? = null,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addFreeChatItemEntities(entities: Collection<FreeChatTable>)

        @Query("SELECT * FROM free_chat WHERE video_id IN (:ids)")
        suspend fun findFreeChatItems(ids: Collection<YouTubeVideo.Id>): List<FreeChatTable>

        @Query("DELETE FROM free_chat WHERE video_id IN(:ids)")
        suspend fun removeFreeChatItems(ids: Collection<YouTubeVideo.Id>)

        @Query("DELETE FROM free_chat")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "video_expire",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("video_id")],
)
internal class YouTubeVideoExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "video_id")
    val videoId: YouTubeVideo.Id,
    @ColumnInfo(name = "fetched_at", defaultValue = "null")
    val fetchedAt: Instant?,
    @ColumnInfo(name = "max_age", defaultValue = "null")
    val maxAge: Duration?,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addLiveVideoExpire(expire: Collection<YouTubeVideoExpireTable>)

        @Query("UPDATE video_expire SET max_age = :maxAge WHERE video_id IN (:id)")
        suspend fun updateMaxAgeById(id: Collection<YouTubeVideo.Id>, maxAge: Duration)

        @Query("DELETE FROM video_expire WHERE video_id IN (:ids)")
        suspend fun removeLiveVideoExpire(ids: Collection<YouTubeVideo.Id>)

        @Query("DELETE FROM video_expire")
        override suspend fun deleteTable()
    }
}

@Entity(tableName = "yt_video_is_archived")
internal class YouTubeVideoIsArchivedTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("video_id")
    val videoId: YouTubeVideo.Id, // archived video is not cached so not to be constrained by foreign key
    @ColumnInfo("is_archived")
    val isArchived: Boolean? = null,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addVideoIsArchivedEntities(items: Collection<YouTubeVideoIsArchivedTable>)

        @Query("DELETE FROM yt_video_is_archived WHERE video_id IN (:ids)")
        suspend fun removeVideoIsArchivedEntities(ids: Collection<YouTubeVideo.Id>)

        @Query(
            "DELETE FROM yt_video_is_archived WHERE video_id NOT IN " +
                "(SELECT video_id FROM playlist_item UNION SELECT video_id FROM free_chat)"
        )
        suspend fun removeUnusedEntities()

        @Query("DELETE FROM yt_video_is_archived")
        override suspend fun deleteTable()
    }
}

internal interface YouTubeVideoDaoProviders {
    val youTubeVideoDao: YouTubeVideoTable.Dao
    val youtubeVideoDbDao: YouTubeVideoDb.Dao
    val youTubeVideoExpireDao: YouTubeVideoExpireTable.Dao
    val youTubeFreeChatDao: FreeChatTable.Dao
    val youTubeVideoIsArchivedDao: YouTubeVideoIsArchivedTable.Dao
}

internal interface YouTubeVideoDao : YouTubeVideoTable.Dao, YouTubeVideoDb.Dao,
    YouTubeVideoExpireTable.Dao, YouTubeVideoIsArchivedTable.Dao, FreeChatTable.Dao

internal class YouTubeVideoDaoImpl @Inject constructor(
    private val db: YouTubeVideoDaoProviders
) : YouTubeVideoDao, YouTubeVideoTable.Dao by db.youTubeVideoDao,
    YouTubeVideoDb.Dao by db.youtubeVideoDbDao,
    YouTubeVideoExpireTable.Dao by db.youTubeVideoExpireDao,
    YouTubeVideoIsArchivedTable.Dao by db.youTubeVideoIsArchivedDao,
    FreeChatTable.Dao by db.youTubeFreeChatDao {
    override suspend fun deleteTable() {
        listOf(
            db.youTubeVideoDao, db.youTubeVideoIsArchivedDao,
            db.youTubeVideoExpireDao, db.youTubeFreeChatDao
        ).forEach { it.deleteTable() }
    }
}
