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
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchLiveChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.source.local.TableDeletable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailDbView.Companion.SQL_EMBED_ALIAS
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailDbView.Companion.SQL_EMBED_PREFIX
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailDbView.Companion.SQL_USER_DETAIL
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
    ],
)
internal class TwitchStreamScheduleTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: TwitchChannelSchedule.Stream.Id,
    @ColumnInfo(name = "start_time")
    override val startTime: Instant,
    @ColumnInfo(name = "end_time")
    override val endTime: Instant?,
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "canceled_until")
    override val canceledUntil: String?,
    @Embedded
    override val category: TwitchStreamCategory?,
    @ColumnInfo(name = "is_recurring")
    override val isRecurring: Boolean,
    @ColumnInfo(name = "user_id", index = true)
    val userId: TwitchUser.Id,
) : TwitchChannelSchedule.Stream {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addChannelStreamSchedules(streams: Collection<TwitchStreamScheduleTable>)

        @Query("SELECT * FROM twitch_channel_schedule_stream AS s WHERE s.id = :id")
        suspend fun findStreamScheduleEntity(id: TwitchChannelSchedule.Stream.Id): TwitchStreamScheduleTable?

        @Query("DELETE FROM twitch_channel_schedule_stream WHERE user_id IN (:ids)")
        suspend fun removeChannelStreamSchedulesByUserIds(ids: Collection<TwitchUser.Id>)

        @Query("DELETE FROM twitch_channel_schedule_stream WHERE id IN (:ids)")
        suspend fun removeChannelStreamSchedulesByIds(ids: Collection<TwitchChannelSchedule.Stream.Id>)

        @Query("DELETE FROM twitch_channel_schedule_stream")
        override suspend fun deleteTable()
    }
}

internal class TwitchChannelScheduleDb(
    @Relation(
        parentColumn = "${SQL_EMBED_PREFIX}id",
        entityColumn = "user_id"
    )
    override val segments: List<TwitchStreamScheduleTable>?,
    @Embedded(SQL_EMBED_PREFIX)
    override val broadcaster: TwitchUserDetailDbView,
    @Embedded
    override val vacation: TwitchChannelVacationSchedule?,
) : TwitchLiveChannelSchedule {
    @androidx.room.Dao
    internal interface Dao {
        @Transaction
        @Query(
            "SELECT s.vacation_start, s.vacation_end, $SQL_EMBED_ALIAS " +
                "FROM (SELECT ss.* FROM twitch_channel_schedule_vacation AS ss " +
                " INNER JOIN twitch_channel_schedule_expire AS e ON ss.user_id = e.user_id " +
                " WHERE :current < e.expired_at " +
                ") AS s " +
                "INNER JOIN ($SQL_USER_DETAIL) AS u ON s.user_id = u.id " +
                "WHERE u.id = :id"
        )
        suspend fun findChannelSchedule(
            id: TwitchUser.Id,
            current: Instant,
        ): List<TwitchChannelScheduleDb>

        @Transaction
        @Query(
            "SELECT s.vacation_start, s.vacation_end, $SQL_EMBED_ALIAS " +
                "FROM twitch_channel_schedule_vacation AS s " +
                "INNER JOIN ($SQL_USER_DETAIL) AS u ON s.user_id = u.id"
        )
        fun watchChannelSchedule(): Flow<List<TwitchChannelScheduleDb>>
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

internal class TwitchStreamCategory(
    @ColumnInfo(name = "category_id")
    override val id: String,
    @ColumnInfo(name = "category_name")
    override val name: String,
) : TwitchChannelSchedule.StreamCategory

internal interface TwitchScheduleDaoProviders {
    val twitchChannelScheduleVacationDao: TwitchChannelVacationScheduleTable.Dao
    val twitchChannelScheduleStreamDao: TwitchStreamScheduleTable.Dao
    val twitchChannelScheduleExpireDao: TwitchChannelScheduleExpireTable.Dao
    val twitchScheduleDbDao: TwitchChannelScheduleDb.Dao
}

internal interface TwitchScheduleDao : TwitchChannelVacationScheduleTable.Dao,
    TwitchStreamScheduleTable.Dao, TwitchChannelScheduleExpireTable.Dao, TwitchChannelScheduleDb.Dao

internal class TwitchScheduleDaoImpl @Inject constructor(
    private val db: TwitchScheduleDaoProviders,
) : TwitchScheduleDao,
    TwitchChannelVacationScheduleTable.Dao by db.twitchChannelScheduleVacationDao,
    TwitchStreamScheduleTable.Dao by db.twitchChannelScheduleStreamDao,
    TwitchChannelScheduleExpireTable.Dao by db.twitchChannelScheduleExpireDao,
    TwitchChannelScheduleDb.Dao by db.twitchScheduleDbDao {
    override suspend fun deleteTable() {
        listOf(
            db.twitchChannelScheduleStreamDao,
            db.twitchChannelScheduleVacationDao,
            db.twitchChannelScheduleExpireDao,
        ).forEach { it.deleteTable() }
    }
}
