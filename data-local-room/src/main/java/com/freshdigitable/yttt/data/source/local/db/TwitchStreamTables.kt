package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.source.local.TableDeletable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailDbView.Companion.SQL_EMBED_PREFIX
import kotlinx.coroutines.flow.Flow
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
    @ColumnInfo(name = "game_id")
    val gameId: String,
    @ColumnInfo(name = "game_name")
    val gameName: String,
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

private const val SQL_USER_DETAIL_ = "SELECT u.*, d.profile_image_url, d.views_count, " +
    "d.created_at, d.description FROM twitch_user_detail AS d " +
    "INNER JOIN twitch_user AS u ON d.user_id = u.id"
private const val SQL_EMBED_ALIAS_ = "u.id AS ${SQL_EMBED_PREFIX}id, " +
    "u.display_name AS ${SQL_EMBED_PREFIX}display_name, u.login_name AS ${SQL_EMBED_PREFIX}login_name, " +
    "u.description AS ${SQL_EMBED_PREFIX}description, u.created_at AS ${SQL_EMBED_PREFIX}created_at, " +
    "u.views_count AS ${SQL_EMBED_PREFIX}views_count, u.profile_image_url AS ${SQL_EMBED_PREFIX}profile_image_url"

@DatabaseView(
    viewName = "twitch_stream_view",
    value = "SELECT s.*, $SQL_EMBED_ALIAS_ FROM twitch_stream AS s " +
        "INNER JOIN ($SQL_USER_DETAIL_) AS u ON u.id = s.user_id",
)
internal data class TwitchStreamDbView(
    @Embedded
    private val streamEntity: TwitchStreamTable,
    @Embedded(SQL_EMBED_PREFIX)
    override val user: TwitchUserDetailDbView,
    @ColumnInfo("u_views_count")
    private val viewsCount: Int,
) : TwitchStream {
    override val gameId: String get() = streamEntity.gameId
    override val gameName: String get() = streamEntity.gameName
    override val type: String get() = streamEntity.type
    override val startedAt: Instant get() = streamEntity.startedAt
    override val tags: List<String> get() = streamEntity.tags
    override val isMature: Boolean get() = streamEntity.isMature
    override val id: TwitchStream.Id get() = streamEntity.id
    override val title: String get() = streamEntity.title
    override val thumbnailUrlBase: String get() = streamEntity.thumbnailUrlBase
    override val viewCount: Int get() = streamEntity.viewCount
    override val language: String get() = streamEntity.language

    @androidx.room.Dao
    internal interface Dao {
        @Query("SELECT * FROM twitch_stream_view AS v WHERE v.id = :id")
        suspend fun findStream(id: TwitchStream.Id): TwitchStreamDbView

        @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
        fun watchStream(): Flow<List<TwitchStreamDbView>>

        @Query("SELECT * FROM twitch_stream_view AS v ORDER BY v.started_at DESC")
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
    @ColumnInfo("expired_at")
    val expiredAt: Instant,
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

internal interface TwitchStreamDao : TwitchStreamTable.Dao, TwitchStreamExpireTable.Dao,
    TwitchStreamDbView.Dao

internal class TwitchStreamDaoImpl @Inject constructor(
    private val db: TwitchStreamDaoProviders,
) : TwitchStreamDao, TwitchStreamTable.Dao by db.twitchStreamDao,
    TwitchStreamExpireTable.Dao by db.twitchStreamExpireDao,
    TwitchStreamDbView.Dao by db.twitchStreamViewDao {
    override suspend fun deleteTable() {
        listOf(db.twitchStreamDao, db.twitchStreamExpireDao).forEach { it.deleteTable() }
    }
}
