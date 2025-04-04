package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.source.local.TableDeletable
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

@Entity(
    tableName = "twitch_channel_schedule_vacation",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
internal class TwitchChannelVacationScheduleTable(
    @PrimaryKey
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
    @Embedded
    val vacation: TwitchChannelVacationSchedule?,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelVacationSchedules(streams: Collection<TwitchChannelVacationScheduleTable>)

        @Query("SELECT * FROM twitch_channel_schedule_vacation WHERE user_id = :userId")
        suspend fun findVacationById(userId: TwitchUser.Id): TwitchChannelVacationScheduleTable?

        @Query("DELETE FROM twitch_channel_schedule_vacation WHERE user_id IN (:ids)")
        suspend fun removeChannelVacationSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

        @Query("DELETE FROM twitch_channel_schedule_vacation")
        override suspend fun deleteTable()
    }
}

internal class TwitchChannelVacationSchedule(
    @ColumnInfo(name = "vacation_start")
    override val startTime: Instant,
    @ColumnInfo(name = "vacation_end")
    override val endTime: Instant,
) : TwitchChannelSchedule.Vacation

@Entity(
    tableName = "twitch_channel_schedule_stream",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
        ForeignKey(
            entity = TwitchCategoryTable::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
    ],
)
internal class TwitchStreamScheduleTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: TwitchChannelSchedule.Stream.Id,
    @ColumnInfo(name = "start_time")
    val startTime: Instant,
    @ColumnInfo(name = "end_time")
    val endTime: Instant?,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "canceled_until")
    val canceledUntil: String?,
    @ColumnInfo(name = "category_id", index = true)
    val categoryId: TwitchCategory.Id?,
    @ColumnInfo(name = "is_recurring")
    val isRecurring: Boolean,
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelStreamSchedules(streams: Collection<TwitchStreamScheduleTable>)

        @Query("DELETE FROM twitch_channel_schedule_stream WHERE user_id IN (:ids)")
        suspend fun removeChannelStreamSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

        @Query("DELETE FROM twitch_channel_schedule_stream WHERE id IN (:ids)")
        suspend fun removeChannelStreamSchedulesByIds(ids: Collection<TwitchChannelSchedule.Stream.Id>)

        @Query("DELETE FROM twitch_channel_schedule_stream")
        override suspend fun deleteTable()
    }
}

internal class TwitchChannelScheduleStream(
    @Embedded private val schedule: TwitchStreamScheduleTable,
    @ColumnInfo(name = "category_name") private val categoryName: String?,
    @ColumnInfo(name = "category_art_url_base") private val categoryArtUrlBase: String?,
    @ColumnInfo(name = "category_igdb_id") private val categoryIgdbId: String?,
) : TwitchChannelSchedule.Stream {
    override val id: TwitchChannelSchedule.Stream.Id get() = schedule.id
    override val startTime: Instant get() = schedule.startTime
    override val endTime: Instant? get() = schedule.endTime
    override val title: String get() = schedule.title
    override val canceledUntil: String? get() = schedule.canceledUntil
    override val isRecurring: Boolean get() = schedule.isRecurring
    val userId: TwitchUser.Id get() = schedule.userId
    override val category: TwitchCategory?
        get() = schedule.categoryId?.let {
            TwitchCategoryTable(it, categoryName ?: "", categoryArtUrlBase, categoryIgdbId)
        }

    @androidx.room.Dao
    interface Dao {
        companion object {
            internal const val SQL_STREAM_SCHEDULE =
                "SELECT s.*, c.name AS category_name, c.art_url_base AS category_art_url_base, c.igdb_id AS category_igdb_id " +
                    "FROM twitch_channel_schedule_stream AS s " +
                    "LEFT OUTER JOIN twitch_category AS c ON s.category_id = c.id"
        }

        @Query("$SQL_STREAM_SCHEDULE WHERE s.id = :id")
        suspend fun findStreamScheduleEntity(id: TwitchChannelSchedule.Stream.Id): TwitchChannelScheduleStream?

        @Query("$SQL_STREAM_SCHEDULE WHERE s.user_id = :id")
        suspend fun findStreamScheduleByUserId(id: TwitchUser.Id): List<TwitchChannelScheduleStream>
    }
}

@Entity(
    tableName = "twitch_channel_schedule_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchChannelVacationScheduleTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
internal class TwitchChannelScheduleExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("user_id")
    val userId: TwitchUser.Id,
    @ColumnInfo("expired_at")
    val expiredAt: Instant,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelScheduleExpireEntity(schedule: Collection<TwitchChannelScheduleExpireTable>)

        @Query("DELETE FROM twitch_channel_schedule_expire WHERE user_id IN (:id)")
        suspend fun removeChannelScheduleExpireEntity(id: Collection<TwitchUser.Id>)

        @Query("DELETE FROM twitch_channel_schedule_expire")
        override suspend fun deleteTable()
    }
}

@Entity(tableName = "twitch_category")
internal class TwitchCategoryTable(
    @PrimaryKey
    @ColumnInfo(name = "id")
    override val id: TwitchCategory.Id,
    @ColumnInfo(name = "name")
    override val name: String,
    @ColumnInfo(name = "art_url_base")
    override val artUrlBase: String? = null,
    @ColumnInfo(name = "igdb_id")
    override val igdbId: String? = null,
) : TwitchCategory {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Query("SELECT * FROM twitch_category WHERE id IN (:id)")
        suspend fun findCategoryById(id: Set<TwitchCategory.Id>): List<TwitchCategoryTable>

        @Upsert
        suspend fun addCategories(categories: Collection<TwitchCategoryTable>)

        @Query("DELETE FROM twitch_category")
        override suspend fun deleteTable()
    }
}

internal class TwitchLiveScheduleDb(
    @Embedded(prefix = TwitchUserDetailDbView.SQL_EMBED_PREFIX)
    override val user: TwitchUserDetailDbView,
    @Embedded
    override val schedule: TwitchChannelScheduleStream,
) : TwitchLiveSchedule {
    override val thumbnailUrlBase: String get() = schedule.category?.artUrlBase ?: ""

    @androidx.room.Dao
    interface Dao {
        @Query(
            "SELECT s.*, c.name AS category_name, c.art_url_base AS category_art_url_base, " +
                "c.igdb_id AS category_igdb_id, ${TwitchUserDetailDbView.SQL_EMBED_ALIAS} " +
                "FROM twitch_channel_schedule_stream AS s " +
                "INNER JOIN twitch_category AS c ON s.category_id = c.id " +
                "INNER JOIN (${TwitchUserDetailDbView.SQL_USER_DETAIL}) AS u ON s.user_id = u.id"
        )
        fun watchLiveSchedule(): Flow<List<TwitchLiveScheduleDb>>
    }
}

internal interface TwitchScheduleDaoProviders {
    val twitchChannelScheduleVacationDao: TwitchChannelVacationScheduleTable.Dao
    val twitchChannelScheduleStreamDao: TwitchStreamScheduleTable.Dao
    val twitchChannelScheduleExpireDao: TwitchChannelScheduleExpireTable.Dao
    val twitchScheduleStreamDao: TwitchChannelScheduleStream.Dao
    val twitchCategoryDao: TwitchCategoryTable.Dao
    val twitchLiveScheduleDao: TwitchLiveScheduleDb.Dao
}

internal interface TwitchScheduleDao : TwitchChannelVacationScheduleTable.Dao,
    TwitchStreamScheduleTable.Dao, TwitchChannelScheduleExpireTable.Dao,
    TwitchCategoryTable.Dao, TwitchChannelScheduleStream.Dao, TwitchLiveScheduleDb.Dao

internal class TwitchScheduleDaoImpl @Inject constructor(
    private val db: TwitchScheduleDaoProviders,
) : TwitchScheduleDao,
    TwitchChannelVacationScheduleTable.Dao by db.twitchChannelScheduleVacationDao,
    TwitchStreamScheduleTable.Dao by db.twitchChannelScheduleStreamDao,
    TwitchChannelScheduleExpireTable.Dao by db.twitchChannelScheduleExpireDao,
    TwitchChannelScheduleStream.Dao by db.twitchScheduleStreamDao,
    TwitchCategoryTable.Dao by db.twitchCategoryDao,
    TwitchLiveScheduleDb.Dao by db.twitchLiveScheduleDao {
    override suspend fun deleteTable() {
        listOf(
            db.twitchChannelScheduleStreamDao,
            db.twitchChannelScheduleVacationDao,
            db.twitchChannelScheduleExpireDao,
            db.twitchCategoryDao,
        ).forEach { it.deleteTable() }
    }
}
