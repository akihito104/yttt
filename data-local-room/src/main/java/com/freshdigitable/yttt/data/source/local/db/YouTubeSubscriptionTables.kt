package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.time.Instant
import javax.inject.Inject

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
internal class YouTubeSubscriptionTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    val id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    val subscribeSince: Instant,
    @ColumnInfo(name = "channel_id")
    val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "subs_order", defaultValue = Int.MAX_VALUE.toString())
    val order: Int = Int.MAX_VALUE,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addSubscriptionEntities(subscriptions: Collection<YouTubeSubscriptionTable>)

        @Query("DELETE FROM subscription WHERE id IN (:removed)")
        suspend fun removeSubscriptions(removed: Collection<YouTubeSubscription.Id>)

        @Query("SELECT id FROM subscription ORDER BY subs_order ASC")
        suspend fun fetchAllSubscriptionIds(): List<YouTubeSubscription.Id>

        @Query("DELETE FROM subscription")
        override suspend fun deleteTable()
    }
}

internal data class YouTubeSubscriptionDb(
    @ColumnInfo(name = "id")
    override val id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    override val subscribeSince: Instant,
    @Embedded(prefix = "channel_")
    override val channel: YouTubeChannelTable,
    @ColumnInfo(name = "subs_order")
    override val order: Int,
) : YouTubeSubscription {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT s.*, c.title AS channel_title, c.icon AS channel_icon FROM subscription AS s " +
                "INNER JOIN channel AS c ON c.id = s.channel_id ORDER BY subs_order ASC"
        )
        suspend fun findAllSubscriptions(): List<YouTubeSubscriptionDb>
    }
}

internal data class YouTubeSubscriptionSummaryDb(
    @ColumnInfo("subscription_id")
    override val subscriptionId: YouTubeSubscription.Id,
    @ColumnInfo("channel_id")
    override val channelId: YouTubeChannel.Id,
    @ColumnInfo("uploaded_playlist_id")
    override val uploadedPlaylistId: YouTubePlaylist.Id?,
    @ColumnInfo("playlist_expired_at")
    override val playlistExpiredAt: Instant?,
) : YouTubeSubscriptionSummary {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT s.id AS subscription_id, s.channel_id, c.uploaded_playlist_id, " +
                "(c.last_modified + c.max_age) AS playlist_expired_at FROM subscription AS s " +
                "LEFT OUTER JOIN ( " +
                " SELECT c.id, c.uploaded_playlist_id, p.max_age, p.last_modified FROM channel_addition AS c " +
                " INNER JOIN playlist AS p ON c.uploaded_playlist_id = p.id " +
                ") AS c ON s.channel_id = c.id " +
                "WHERE subscription_id IN (:ids)"
        )
        fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummaryDb>
    }
}

internal interface YouTubeSubscriptionDaoProviders {
    val youTubeSubscriptionDao: YouTubeSubscriptionTable.Dao
    val youtubeSubscriptionDbDao: YouTubeSubscriptionDb.Dao
    val youTubeSubscriptionSummaryDbDao: YouTubeSubscriptionSummaryDb.Dao
}

internal interface YouTubeSubscriptionDao : YouTubeSubscriptionTable.Dao,
    YouTubeSubscriptionDb.Dao, YouTubeSubscriptionSummaryDb.Dao

internal class YouTubeSubscriptionDaoImpl @Inject constructor(
    db: YouTubeSubscriptionDaoProviders,
) : YouTubeSubscriptionDao, YouTubeSubscriptionTable.Dao by db.youTubeSubscriptionDao,
    YouTubeSubscriptionDb.Dao by db.youtubeSubscriptionDbDao,
    YouTubeSubscriptionSummaryDb.Dao by db.youTubeSubscriptionSummaryDbDao
