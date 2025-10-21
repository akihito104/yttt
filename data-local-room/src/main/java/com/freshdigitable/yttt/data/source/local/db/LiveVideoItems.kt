package com.freshdigitable.yttt.data.source.local.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.ProvidedTypeConverter
import androidx.room.Query
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.AppDatabase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

internal data class LiveVideoOnAirItem(
    @ColumnInfo("id_value") private val idValue: String,
    @ColumnInfo("platform") private val platform: LivePlatform,
    @ColumnInfo("title") override val title: String,
    @ColumnInfo("c_id") private val channelIdValue: String,
    @ColumnInfo("c_title") private val channelTitle: String,
    @ColumnInfo("c_icon") private val channelIconUrl: String,
    @ColumnInfo("date_time") override val dateTime: Instant,
    @ColumnInfo("thumbnail") override val thumbnailUrl: String,
) : LiveVideo {
    override val id: LiveVideo.Id get() = LiveVideo.Id(idValue, platform.onAirVideoIdType)
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = LiveChannel.Id(channelIdValue, platform.channelIdType),
            title = channelTitle,
            iconUrl = channelIconUrl,
            platform = platform,
        )
    override val isPinned: Boolean? get() = null

    @androidx.room.Dao
    internal interface Dao {
        companion object {
            private const val TWITCH_IMG_WIDTH = "'{width}', '640'"
            private const val TWITCH_IMG_HEIGHT = "'{height}', '360'"
            private const val SQL_YOUTUBE_ON_AIR =
                "SELECT yd.video_id AS id_value, yd.title AS title, yc.id AS c_id, yc.icon AS c_icon," +
                    " yc.title AS c_title, yd.thumbnail AS thumbnail," +
                    " yd.actual_start_datetime AS date_time, 'YouTube' AS platform " +
                    "FROM video_detail AS yd " +
                    "INNER JOIN video AS yv ON yv.id = yd.video_id " +
                    "INNER JOIN channel AS yc ON yc.id = yd.channel_id " +
                    "WHERE yv.broadcast_content = 'live'"
            private const val SQL_TWITCH_ON_AIR =
                "SELECT ts.id AS id_value, ts.title AS title, tu.user_id AS c_id, tu.profile_image_url AS c_icon," +
                    " tu.display_name AS c_title," +
                    " REPLACE(REPLACE(ts.thumbnail_url_base, $TWITCH_IMG_WIDTH), $TWITCH_IMG_HEIGHT) AS thumbnail," +
                    " ts.started_at AS date_time, 'Twitch' AS platform " +
                    "FROM twitch_stream AS ts " +
                    "INNER JOIN twitch_user_detail_view AS tu ON tu.user_id = ts.user_id " +
                    "INNER JOIN twitch_category AS c ON c.id = ts.game_id "
        }

        @Query("$SQL_YOUTUBE_ON_AIR UNION $SQL_TWITCH_ON_AIR ORDER BY date_time DESC, title ASC")
        fun getAllOnAirPagingSource(): PagingSource<Int, LiveVideoOnAirItem>
    }
}

internal data class LiveVideoUpcomingItem(
    @ColumnInfo("id_value") private val idValue: String,
    @ColumnInfo("platform") private val platform: LivePlatform,
    @ColumnInfo("title") override val title: String,
    @ColumnInfo("c_id") private val channelIdValue: String,
    @ColumnInfo("c_title") private val channelTitle: String,
    @ColumnInfo("c_icon") private val channelIconUrl: String,
    @ColumnInfo("date_time") override val dateTime: Instant,
    @ColumnInfo("thumbnail") override val thumbnailUrl: String,
    @ColumnInfo("is_landscape", defaultValue = "1") override val isLandscape: Boolean = true,
) : LiveVideo {
    override val id: LiveVideo.Id get() = LiveVideo.Id(idValue, platform.upcomingVideoIdType)
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = LiveChannel.Id(channelIdValue, platform.channelIdType),
            title = channelTitle,
            iconUrl = channelIconUrl,
            platform = platform,
        )
    override val isPinned: Boolean? get() = null

    @androidx.room.Dao
    internal interface Dao {
        companion object {
            private const val PUBLICATION_DEADLINE = "${LiveVideo.UPCOMING_DEADLINE_HOURS} hours"
            private const val TWITCH_PUBLICATION_TERM = "7 days"
            private const val TWITCH_IMG_WIDTH = "'{width}', '240'"
            private const val TWITCH_IMG_HEIGHT = "'{height}', '360'"
            private const val SQL_YOUTUBE_UPCOMING =
                "SELECT d.video_id AS id_value, d.title AS title, c.id AS c_id, c.icon AS c_icon," +
                    " c.title AS c_title, d.thumbnail AS thumbnail," +
                    " d.schedule_start_datetime AS date_time, 'YouTube' AS platform, 1 AS is_landscape " +
                    "FROM video_detail AS d " +
                    "INNER JOIN video AS v ON v.id = d.video_id " +
                    "INNER JOIN channel AS c ON c.id = d.channel_id " +
                    "LEFT OUTER JOIN free_chat AS f ON d.video_id = f.video_id " +
                    "WHERE v.broadcast_content = 'upcoming' AND f.is_free_chat = 0" +
                    " AND d.schedule_start_datetime IS NOT NULL" +
                    " AND DATETIME(:current/1000, 'unixepoch') <= " + // adjust to BETWEEN ... AND condition
                    "  DATETIME(d.schedule_start_datetime/1000, 'unixepoch', '+$PUBLICATION_DEADLINE')"
            private const val SQL_TWITCH_UPCOMING =
                "SELECT ts.id AS id_value, ts.title AS title, ts.user_id AS c_id, tu.profile_image_url AS c_icon," +
                    " tu.display_name AS c_title," +
                    " CASE WHEN tc.art_url_base = '' OR tc.art_url_base IS NULL THEN ''" +
                    "  ELSE REPLACE(REPLACE(tc.art_url_base, $TWITCH_IMG_WIDTH), $TWITCH_IMG_HEIGHT) " +
                    " END AS thumbnail," +
                    " ts.start_time AS date_time, 'Twitch' AS platform, 0 AS is_landscape " +
                    "FROM twitch_channel_schedule_stream AS ts " +
                    "LEFT OUTER JOIN twitch_category AS tc ON ts.category_id = tc.id " +
                    "INNER JOIN twitch_user_detail_view AS tu ON ts.user_id = tu.user_id " +
                    "WHERE DATETIME(ts.start_time/1000, 'unixepoch')" +
                    " BETWEEN DATETIME(:current/1000, 'unixepoch', '-$PUBLICATION_DEADLINE') " +
                    " AND DATETIME(:current/1000, 'unixepoch', '+$TWITCH_PUBLICATION_TERM')"
        }

        @Query("$SQL_YOUTUBE_UPCOMING UNION $SQL_TWITCH_UPCOMING ORDER BY date_time ASC, title ASC")
        fun getAllUpcomingPagingSource(current: Instant): PagingSource<Int, LiveVideoUpcomingItem>
    }
}

internal data class LiveVideoFreeChatItem(
    @ColumnInfo("id_value") private val idValue: String,
    @ColumnInfo("platform") private val platform: LivePlatform,
    @ColumnInfo("title") override val title: String,
    @ColumnInfo("c_id") private val channelIdValue: String,
    @ColumnInfo("c_title") private val channelTitle: String,
    @ColumnInfo("c_icon") private val channelIconUrl: String,
    @ColumnInfo("date_time") override val dateTime: Instant,
    @ColumnInfo("thumbnail") override val thumbnailUrl: String,
    @ColumnInfo("is_pinned") override val isPinned: Boolean? = null,
) : LiveVideo {
    override val id: LiveVideo.Id get() = LiveVideo.Id(idValue, platform.freeChatVideoIdType)
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = LiveChannel.Id(channelIdValue, platform.channelIdType),
            title = channelTitle,
            iconUrl = channelIconUrl,
            platform = platform,
        )

    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT d.video_id AS id_value, d.title AS title, c.id AS c_id, c.icon AS c_icon," +
                " c.title AS c_title, d.thumbnail AS thumbnail, p.video_id IS NOT NULL AS is_pinned," +
                " d.schedule_start_datetime AS date_time, 'YouTube' AS platform " +
                "FROM video_detail AS d " +
                "INNER JOIN video AS v ON v.id = d.video_id " +
                "INNER JOIN channel AS c ON c.id = d.channel_id " +
                "LEFT OUTER JOIN yt_pinned AS p ON p.video_id = d.video_id " +
                "LEFT OUTER JOIN free_chat AS f ON d.video_id = f.video_id " +
                "WHERE is_free_chat = 1 AND v.broadcast_content != 'live' " +
                "ORDER BY is_pinned DESC, c_id ASC, date_time ASC, title ASC",
        )
        fun getAllFreeChatPagingSource(): PagingSource<Int, LiveVideoFreeChatItem>
    }
}

internal interface LiveVideoItemDaoProviders {
    val liveVideoOnAirItemDao: LiveVideoOnAirItem.Dao
    val liveVideoUpcomingItemDao: LiveVideoUpcomingItem.Dao
    val liveVideoFreeChatItemDao: LiveVideoFreeChatItem.Dao
}

internal interface LiveVideoItemDao :
    LiveVideoOnAirItem.Dao,
    LiveVideoUpcomingItem.Dao,
    LiveVideoFreeChatItem.Dao

@Singleton
internal class LiveVideoItemDaoImpl @Inject constructor(
    private val db: AppDatabase,
) : LiveVideoItemDao,
    LiveVideoOnAirItem.Dao by db.liveVideoOnAirItemDao,
    LiveVideoUpcomingItem.Dao by db.liveVideoUpcomingItemDao,
    LiveVideoFreeChatItem.Dao by db.liveVideoFreeChatItemDao

internal interface LiveDaoProviders : LiveVideoItemDaoProviders

@Singleton
internal class LiveDao @Inject constructor(
    private val liveVideoItemDao: LiveVideoItemDaoImpl,
) : LiveVideoItemDao by liveVideoItemDao

@ProvidedTypeConverter
internal class LivePlatformConverter(
    platforms: Collection<@JvmSuppressWildcards LivePlatform>,
) : Converter<String, LivePlatform>(
    serialize = { it.name },
    createObject = { platforms.first { platform -> platform.name == it } },
)

private val LivePlatform.onAirVideoIdType: KClass<out IdBase>
    get() = when (this) {
        YouTube -> YouTubeVideo.Id::class
        Twitch -> TwitchStream.Id::class
        else -> error("unknown platform: $this")
    }
private val LivePlatform.upcomingVideoIdType: KClass<out IdBase>
    get() = when (this) {
        YouTube -> YouTubeVideo.Id::class
        Twitch -> TwitchChannelSchedule.Stream.Id::class
        else -> error("unknown platform: $this")
    }
private val LivePlatform.freeChatVideoIdType: KClass<out IdBase>
    get() = when (this) {
        YouTube -> YouTubeVideo.Id::class
        else -> error("unknown platform: $this")
    }
private val LivePlatform.channelIdType: KClass<out IdBase>
    get() = when (this) {
        YouTube -> YouTubeChannel.Id::class
        Twitch -> TwitchUser.Id::class
        else -> error("unknown platform: $this")
    }
