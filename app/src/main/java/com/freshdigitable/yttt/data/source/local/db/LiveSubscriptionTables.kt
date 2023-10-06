package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveSubscription
import java.time.Instant

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
