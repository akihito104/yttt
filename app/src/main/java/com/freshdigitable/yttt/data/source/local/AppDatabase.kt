package com.freshdigitable.yttt.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelAddition
import com.freshdigitable.yttt.data.model.LiveChannelDetail
import com.freshdigitable.yttt.data.model.LiveChannelLog
import com.freshdigitable.yttt.data.model.LivePlaylist
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.LiveVideo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.math.BigInteger
import java.time.Instant

@Database(
    entities = [
        LiveChannelTable::class,
        LiveChannelAdditionTable::class,
        LiveChannelLogTable::class,
        LiveSubscriptionTable::class,
        LiveVideoTable::class,
        FreeChatTable::class,
    ],
    views = [
        LiveVideoDbView::class,
        LiveSubscriptionDbView::class,
        LiveChannelDetailDbView::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ]
)
@TypeConverters(
    InstantConverter::class,
    LiveChannelIdConverter::class,
    LiveSubscriptionIdConverter::class,
    LiveVideoIdConverter::class,
    LiveChannelLogIdConverter::class,
    LivePlaylistIdConverter::class,
    BigIntegerConverter::class,
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
    @ColumnInfo(name = "visible", defaultValue = true.toString())
    val visible: Boolean = true,
)

@Entity(
    tableName = "free_chat",
    foreignKeys = [
        ForeignKey(
            entity = LiveVideoTable::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
        ),
    ],
    indices = [Index("video_id")],
)
class FreeChatTable(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Int = 0,
    @ColumnInfo("video_id")
    val videoId: LiveVideo.Id,
)

@DatabaseView(
    "SELECT v.id, v.title, v.channel_id, v.schedule_start_datetime, v.schedule_end_datetime, " +
        "v.actual_start_datetime, v.actual_end_datetime, v.thumbnail, " +
        "c.title AS channel_title, c.icon AS channel_icon, (f.id NOTNULL) AS is_free_chat " +
        "FROM video AS v INNER JOIN channel AS c ON c.id = v.channel_id " +
        "LEFT OUTER JOIN free_chat AS f ON v.id = f.video_id " +
        "WHERE v.visible == 1",
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
    @ColumnInfo(name = "is_free_chat", defaultValue = "false")
    override val isFreeChat: Boolean,
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
    tableName = "channel_addition",
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["id"],
        ),
    ],
)
data class LiveChannelAdditionTable(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: LiveChannel.Id,
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
    @ColumnInfo(name = "uploaded_playlist_id")
    override val uploadedPlayList: LivePlaylist.Id?,
) : LiveChannelAddition {
    override val keywords: Collection<String>
        get() = keywordsRaw.split(",", " ")
}

@DatabaseView(
    "SELECT c.icon, c.title, a.* FROM channel AS c INNER JOIN channel_addition AS a ON c.id = a.id",
    viewName = "channel_detail",
)
data class LiveChannelDetailDbView(
    @ColumnInfo(name = "title")
    override val title: String,
    @ColumnInfo(name = "icon")
    override val iconUrl: String,
    @Embedded
    val addition: LiveChannelAdditionTable,
) : LiveChannelDetail, LiveChannel, LiveChannelAddition by addition {
    @Ignore
    override val id: LiveChannel.Id = addition.id
}

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
    @ColumnInfo(name = "subs_order", defaultValue = Int.MAX_VALUE.toString())
    val order: Int = Int.MAX_VALUE,
)

@DatabaseView(
    "SELECT s.*, c.title AS channel_title, c.icon AS channel_icon " +
        "FROM subscription AS s " +
        "INNER JOIN channel AS c ON c.id = s.channel_id " +
        "ORDER BY subs_order ASC",
    viewName = "subscription_view"
)
data class LiveSubscriptionDbView(
    @ColumnInfo(name = "id")
    override val id: LiveSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    override val subscribeSince: Instant,
    @Embedded(prefix = "channel_")
    override val channel: LiveChannelTable,
    @ColumnInfo(name = "subs_order")
    override val order: Int,
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

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytttdb")
            .build()
}
