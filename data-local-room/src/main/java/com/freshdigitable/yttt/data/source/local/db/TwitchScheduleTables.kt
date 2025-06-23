package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.Updatable
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
    @ColumnInfo(name = "id") override val id: TwitchChannelSchedule.Stream.Id,
    @ColumnInfo(name = "start_time") override val startTime: Instant,
    @ColumnInfo(name = "end_time") override val endTime: Instant?,
    @ColumnInfo(name = "title") override val title: String,
    @ColumnInfo(name = "canceled_until") override val canceledUntil: String?,
    @ColumnInfo(name = "is_recurring") override val isRecurring: Boolean,
    @Embedded("category_") override val category: TwitchCategoryTable?,
) : TwitchChannelSchedule.Stream {
    @androidx.room.Dao
    internal interface Dao {
        companion object {
            private const val SQL_STREAM_SCHEDULE =
                "SELECT s.id, s.start_time, s.end_time, s.title, s.canceled_until, s.is_recurring, s.category_id, " +
                    "c.name AS category_name, c.art_url_base AS category_art_url_base, c.igdb_id AS category_igdb_id " +
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
    @ColumnInfo("user_id") val userId: TwitchUser.Id,
    @Embedded override val cacheControl: CacheControlDb,
) : Updatable {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelScheduleExpireEntity(schedule: Collection<TwitchChannelScheduleExpireTable>)

        @Query("SELECT * FROM twitch_channel_schedule_expire WHERE user_id = :userId")
        suspend fun findChannelScheduleExpire(userId: TwitchUser.Id): TwitchChannelScheduleExpireTable?

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
        suspend fun upsertCategories(categories: Collection<TwitchCategoryTable>)

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun addCategories(categories: Collection<TwitchCategoryTable>)

        @Query("DELETE FROM twitch_category")
        override suspend fun deleteTable()
    }
}

internal class TwitchLiveScheduleDb(
    @Embedded
    override val user: TwitchUserDetailDbView,
    @Embedded
    override val schedule: TwitchChannelScheduleStream,
) : TwitchLiveSchedule {
    override val thumbnailUrlBase: String get() = schedule.category?.artUrlBase ?: ""

    @androidx.room.Dao
    internal interface Dao {
        companion object {
            private const val SQL_LIVE_SCHEDULE =
                "SELECT s.*, c.name AS category_name, c.art_url_base AS category_art_url_base, " +
                    "c.igdb_id AS category_igdb_id, u.* " +
                    "FROM twitch_channel_schedule_stream AS s " +
                    "LEFT OUTER JOIN twitch_category AS c ON s.category_id = c.id " +
                    "INNER JOIN twitch_user_detail_view AS u ON s.user_id = u.user_id"
        }

        @Query(SQL_LIVE_SCHEDULE)
        fun watchLiveSchedule(): Flow<List<TwitchLiveScheduleDb>>

        @Query("$SQL_LIVE_SCHEDULE WHERE s.id = :id")
        suspend fun findLiveSchedule(id: TwitchChannelSchedule.Stream.Id): TwitchLiveScheduleDb?
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
