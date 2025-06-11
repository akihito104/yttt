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
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelAddition
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeChannelLog
import com.freshdigitable.yttt.data.model.YouTubeChannelTitle
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.math.BigInteger
import java.time.Duration
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
        @Upsert
        suspend fun addChannelAddition(addition: Collection<YouTubeChannelAdditionTable>)

        @Query("DELETE FROM channel_addition")
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
    @ColumnInfo(name = "fetched_at", defaultValue = "null")
    val fetchedAt: Instant? = null,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelAdditionExpire(entities: Collection<YouTubeChannelAdditionExpireTable>)

        @Query("DELETE FROM channel_addition_expire")
        override suspend fun deleteTable()
    }

    companion object {
        internal const val MAX_AGE = 24 * 60 * 60 * 1000L
        internal val MAX_AGE_DURATION = Duration.ofMillis(MAX_AGE)
    }
}

internal data class YouTubeChannelDetailDb(
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "icon")
    override val iconUrl: String,
    @Embedded
    val addition: YouTubeChannelAdditionTable,
    @ColumnInfo(name = "fetched_at")
    override val fetchedAt: Instant,
) : YouTubeChannelDetail, YouTubeChannel, YouTubeChannelAddition by addition {
    @get:Ignore
    override val id: YouTubeChannel.Id get() = addition.id

    @get:Ignore
    override val maxAge: Duration get() = YouTubeChannelAdditionExpireTable.MAX_AGE_DURATION

    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT c.icon AS icon, c.title AS title, a.*, e.fetched_at AS fetched_at FROM channel AS c " +
                "INNER JOIN channel_addition AS a ON c.id = a.id " +
                "INNER JOIN (SELECT * FROM channel_addition_expire WHERE :current < (fetched_at + " +
                "${YouTubeChannelAdditionExpireTable.MAX_AGE})) AS e ON c.id = e.channel_id " +
                "WHERE c.id IN (:id)"
        )
        suspend fun findChannelDetail(
            id: Collection<YouTubeChannel.Id>,
            current: Instant,
        ): List<YouTubeChannelDetailDb>
    }
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
    @Ignore
    override val title: String = "" // TODO

    @Ignore
    override val type: String = "" // TODO

    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelLogEntities(logs: Collection<YouTubeChannelLogTable>)

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
            maxResult: Long? = Long.MAX_VALUE
        ): List<YouTubeChannelLogTable>

        @Query("DELETE FROM channel_log")
        override suspend fun deleteTable()
    }
}

internal interface YouTubeChannelDaoProviders {
    val youTubeChannelDao: YouTubeChannelTable.Dao
    val youTubeChannelAdditionDao: YouTubeChannelAdditionTable.Dao
    val youTubeChannelDetailDbDao: YouTubeChannelDetailDb.Dao
    val youTubeChannelLogDao: YouTubeChannelLogTable.Dao
    val youTubeChannelAdditionExpireDao: YouTubeChannelAdditionExpireTable.Dao
}

internal interface YouTubeChannelDao : YouTubeChannelTable.Dao, YouTubeChannelAdditionTable.Dao,
    YouTubeChannelLogTable.Dao, YouTubeChannelDetailDb.Dao, YouTubeChannelAdditionExpireTable.Dao

internal class YouTubeChannelDaoImpl @Inject constructor(
    private val db: YouTubeChannelDaoProviders,
) : YouTubeChannelDao, YouTubeChannelTable.Dao by db.youTubeChannelDao,
    YouTubeChannelAdditionTable.Dao by db.youTubeChannelAdditionDao,
    YouTubeChannelLogTable.Dao by db.youTubeChannelLogDao,
    YouTubeChannelDetailDb.Dao by db.youTubeChannelDetailDbDao,
    YouTubeChannelAdditionExpireTable.Dao by db.youTubeChannelAdditionExpireDao {
    override suspend fun deleteTable() {
        listOf(
            db.youTubeChannelDao,
            db.youTubeChannelAdditionDao,
            db.youTubeChannelLogDao,
            db.youTubeChannelAdditionExpireDao,
        ).forEach { it.deleteTable() }
    }
}
