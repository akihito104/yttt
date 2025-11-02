package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchLiveStream
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.time.Instant
import javax.inject.Inject

@Entity(
    tableName = "twitch_stream",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
        ForeignKey(
            entity = TwitchCategoryTable::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
        ),
    ],
)
internal class TwitchStreamTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: TwitchStream.Id,
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "thumbnail_url_base")
    val thumbnailUrlBase: String,
    @ColumnInfo(name = "view_count")
    val viewCount: Int,
    @ColumnInfo(name = "language")
    val language: String,
    @ColumnInfo(name = "game_id", index = true)
    val gameId: TwitchCategory.Id,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Instant,
    @ColumnInfo(name = "tags")
    val tags: List<String>,
    @ColumnInfo(name = "is_mature")
    val isMature: Boolean,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addStreams(streams: Collection<TwitchStreamTable>)

        @Query("DELETE FROM twitch_stream")
        override suspend fun deleteTable()
    }
}

internal data class TwitchStreamDbView(
    @ColumnInfo(name = "id") override val id: TwitchStream.Id,
    @ColumnInfo(name = "title") override val title: String,
    @ColumnInfo(name = "thumbnail_url_base") override val thumbnailUrlBase: String,
    @ColumnInfo(name = "view_count") override val viewCount: Int,
    @ColumnInfo(name = "language") override val language: String,
    @ColumnInfo(name = "game_id") override val gameId: TwitchCategory.Id,
    @ColumnInfo(name = "game_name") override val gameName: String,
    @ColumnInfo(name = "type") override val type: String,
    @ColumnInfo(name = "started_at") override val startedAt: Instant,
    @ColumnInfo(name = "tags") override val tags: List<String>,
    @ColumnInfo(name = "is_mature") override val isMature: Boolean,
    @Embedded override val user: TwitchUserDetailDbView,
) : TwitchLiveStream {
    @androidx.room.Dao
    internal interface Dao {
        companion object {
            private const val SQL_STREAM =
                "SELECT s.*, u.created_at AS created_at, u.description AS description," +
                    " u.display_name AS display_name, u.login_name AS login_name," +
                    " u.profile_image_url AS profile_image_url, c.name AS game_name FROM twitch_stream AS s " +
                    "INNER JOIN twitch_user_detail_view AS u ON u.user_id = s.user_id " +
                    "INNER JOIN twitch_category AS c ON c.id = s.game_id"
        }

        @Query("$SQL_STREAM WHERE s.id = :id")
        suspend fun findStream(id: TwitchStream.Id): TwitchStreamDbView

        @Query("$SQL_STREAM ORDER BY s.started_at DESC")
        suspend fun findAllStreams(): List<TwitchStreamDbView>
    }
}

@Entity(
    tableName = "twitch_stream_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
internal class TwitchStreamExpireTable(
    @PrimaryKey
    @ColumnInfo("user_id", index = true)
    val userId: TwitchUser.Id,
    @Embedded
    val cacheControl: CacheControlDb,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Query("SELECT * FROM twitch_stream_expire WHERE user_id = :me")
        suspend fun findStreamExpire(me: TwitchUser.Id): TwitchStreamExpireTable?

        @Upsert
        suspend fun setStreamExpire(expiredAt: TwitchStreamExpireTable)

        @Query("DELETE FROM twitch_stream_expire")
        override suspend fun deleteTable()
    }
}

internal interface TwitchStreamDaoProviders {
    val twitchStreamDao: TwitchStreamTable.Dao
    val twitchStreamExpireDao: TwitchStreamExpireTable.Dao
    val twitchStreamViewDao: TwitchStreamDbView.Dao
}

internal interface TwitchStreamDao :
    TwitchStreamTable.Dao,
    TwitchStreamExpireTable.Dao,
    TwitchStreamDbView.Dao {
    suspend fun setStreamExpireEntity(streams: Updatable<out TwitchStreams>)
    suspend fun addStreamEntities(streams: Collection<TwitchStream>)
}

internal class TwitchStreamDaoImpl @Inject constructor(
    private val db: AppDatabase,
) : TwitchStreamDao,
    TwitchStreamTable.Dao by db.twitchStreamDao,
    TwitchStreamExpireTable.Dao by db.twitchStreamExpireDao,
    TwitchStreamDbView.Dao by db.twitchStreamViewDao {
    override suspend fun setStreamExpireEntity(streams: Updatable<out TwitchStreams>) {
        setStreamExpire(TwitchStreamExpireTable(streams.item.followerId, streams.cacheControl.toDb()))
    }

    override suspend fun addStreamEntities(streams: Collection<TwitchStream>) {
        addStreams(streams.map { it.toTable() })
    }

    override suspend fun deleteTable() {
        listOf(db.twitchStreamDao, db.twitchStreamExpireDao).forEach { it.deleteTable() }
    }
}

private fun TwitchStream.toTable(): TwitchStreamTable = TwitchStreamTable(
    userId = user.id,
    title = title,
    id = id,
    gameId = gameId,
    isMature = isMature,
    language = language,
    startedAt = startedAt,
    tags = tags,
    thumbnailUrlBase = thumbnailUrlBase,
    type = type,
    viewCount = viewCount,
)
