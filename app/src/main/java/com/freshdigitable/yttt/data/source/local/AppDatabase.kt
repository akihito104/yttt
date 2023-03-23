package com.freshdigitable.yttt.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant

@Database(
    entities = [
        LiveChannelTable::class,
        LiveChannelLogTable::class,
        LiveSubscriptionTable::class,
        LiveVideoTable::class,
    ],
    views = [
        LiveVideoDbView::class,
        LiveSubscriptionDbView::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ]
)
@TypeConverters(
    InstantConverter::class,
    LiveChannelIdConverter::class,
    LiveSubscriptionIdConverter::class,
    LiveVideoIdConverter::class,
    LiveChannelLogIdConverter::class,
)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: AppDao
}

@Entity(
    tableName = "video",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
class LiveVideoTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: LiveVideo.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    val title: String = "",
    @ColumnInfo(name = "channel_id")
    val channelId: LiveChannel.Id,
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
)

@DatabaseView(
    "SELECT v.*, c.title AS channel_title, c.icon AS channel_icon " +
        "FROM video AS v " +
        "INNER JOIN channel AS c ON c.id = v.channel_id",
    viewName = "video_view",
)
data class LiveVideoDbView(
    @ColumnInfo(name = "id")
    override val id: LiveVideo.Id,
    @ColumnInfo(name = "title")
    override val title: String,
    @Embedded(prefix = "channel_")
    override val channel: LiveChannelTable,
    @ColumnInfo(name = "schedule_start_datetime")
    override val scheduledStartDateTime: Instant?,
    @ColumnInfo(name = "schedule_end_datetime")
    override val scheduledEndDateTime: Instant?,
    @ColumnInfo(name = "actual_start_datetime")
    override val actualStartDateTime: Instant?,
    @ColumnInfo(name = "actual_end_datetime")
    override val actualEndDateTime: Instant?,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    override val thumbnailUrl: String = "",
) : LiveVideo

@Entity(tableName = "channel")
data class LiveChannelTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: LiveChannel.Id,
    @ColumnInfo(name = "title", defaultValue = "")
    override val title: String = "",
    @ColumnInfo(name = "icon", defaultValue = "")
    override val iconUrl: String = "",
) : LiveChannel

@Entity(
    tableName = "subscription",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
class LiveSubscriptionTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: LiveSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    val subscribeSince: Instant,
    @ColumnInfo(name = "channel_id")
    val channelId: LiveChannel.Id,
)

@DatabaseView(
    "SELECT s.*, c.title AS channel_title, c.icon AS channel_icon " +
        "FROM subscription AS s " +
        "INNER JOIN channel AS c ON c.id = s.channel_id",
    viewName = "subscription_view"
)
data class LiveSubscriptionDbView(
    @ColumnInfo(name = "id")
    override val id: LiveSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    override val subscribeSince: Instant,
    @Embedded(prefix = "channel_")
    override val channel: LiveChannelTable
) : LiveSubscription

@Entity(
    tableName = "channel_log",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
        ForeignKey(
            entity = LiveVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("channel_id"), Index("video_id")],
)
data class LiveChannelLogTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: LiveChannelLog.Id,
    @ColumnInfo(name = "datetime")
    override val dateTime: Instant,
    @ColumnInfo(name = "video_id")
    override val videoId: LiveVideo.Id,
    @ColumnInfo(name = "channel_id")
    override val channelId: LiveChannel.Id,
    @ColumnInfo(name = "thumbnail", defaultValue = "")
    override val thumbnailUrl: String = "",
) : LiveChannelLog

abstract class Converter<S, O>(
    private val serialize: (O) -> S,
    private val createObject: (S) -> O,
) {
    @TypeConverter
    fun toSerial(value: O?): S? = value?.let { serialize(it) }

    @TypeConverter
    fun toObject(value: S?): O? = value?.let { createObject(it) }
}

class InstantConverter : Converter<Long, Instant>(
    serialize = { it.toEpochMilli() },
    createObject = { Instant.ofEpochMilli(it) },
)

abstract class IdConverter<E : IdBase<String>>(createObject: (String) -> E) :
    Converter<String, E>(serialize = { it.value }, createObject = createObject)

class LiveChannelIdConverter : IdConverter<LiveChannel.Id>(createObject = { LiveChannel.Id(it) })

class LiveSubscriptionIdConverter : IdConverter<LiveSubscription.Id>(
    createObject = { LiveSubscription.Id(it) }
)

class LiveVideoIdConverter : IdConverter<LiveVideo.Id>(createObject = { LiveVideo.Id(it) })

class LiveChannelLogIdConverter :
    IdConverter<LiveChannelLog.Id>(createObject = { LiveChannelLog.Id(it) })

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytttdb")
            .build()
}
