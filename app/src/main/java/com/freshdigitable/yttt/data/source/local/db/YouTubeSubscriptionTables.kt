package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemSummary
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.model.YouTubeVideo
import java.time.Instant

@Entity(
    tableName = "subscription",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeChannelTable::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
        ),
    ],
    indices = [Index("channel_id")],
)
class YouTubeSubscriptionTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    val subscribeSince: Instant,
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "subs_order", defaultValue = Int.MAX_VALUE.toString())
    val order: Int = Int.MAX_VALUE,
)

data class YouTubeSubscriptionDb(
    @ColumnInfo(name = "id")
    override val id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    override val subscribeSince: Instant,
    @Embedded(prefix = "channel_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo(name = "subs_order")
    override val order: Int,
) : YouTubeSubscription

data class YouTubeSubscriptionSummaryDb(
    @ColumnInfo("subscription_id")
    override val subscriptionId: YouTubeSubscription.Id,
    @ColumnInfo("channel_id")
    override val channelId: YouTubeChannel.Id,
    @ColumnInfo("uploaded_playlist_id")
    override val uploadedPlaylistId: YouTubePlaylist.Id?,
    @ColumnInfo("playlist_expired_at")
    override val playlistExpiredAt: Instant?,
) : YouTubeSubscriptionSummary

@DatabaseView(
    "SELECT i.playlist_id, i.id AS playlist_item_id, i.video_id, v.is_archived FROM playlist_item AS i " +
        "LEFT OUTER JOIN yt_video_is_archived AS v ON i.video_id = v.video_id",
    viewName = "yt_playlist_item_summary",
)
class YouTubePlaylistItemSummaryDb(
    @ColumnInfo("playlist_id")
    override val playlistId: YouTubePlaylist.Id,
    @ColumnInfo("playlist_item_id")
    override val playlistItemId: YouTubePlaylistItem.Id,
    @ColumnInfo("video_id")
    override val videoId: YouTubeVideo.Id,
    @ColumnInfo("is_archived")
    override val isArchived: Boolean?,
) : YouTubePlaylistItemSummary
