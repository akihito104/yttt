package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeSubscription
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
