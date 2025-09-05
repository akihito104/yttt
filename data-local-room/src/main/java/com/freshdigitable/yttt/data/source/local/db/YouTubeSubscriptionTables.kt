package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionQuery
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionRelevanceOrdered
import com.freshdigitable.yttt.data.model.YouTubeSubscriptionSummary
import com.freshdigitable.yttt.data.source.local.AppDatabase
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
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addSubscriptions(subscriptions: Collection<YouTubeSubscriptionTable>)

        @Query("SELECT id FROM subscription WHERE id NOT IN (:id)")
        suspend fun findSubscriptionIdsByRemainingIds(id: Collection<YouTubeSubscription.Id>): List<YouTubeSubscription.Id>

        @Query("SELECT id FROM subscription")
        suspend fun fetchAllSubscriptionIds(): List<YouTubeSubscription.Id>

        @Query("DELETE FROM subscription WHERE id IN (:id)")
        suspend fun removeSubscriptions(id: Collection<YouTubeSubscription.Id>)

        @Query("DELETE FROM subscription")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "subscription_alphabetical_order_etag",
)
internal data class YouTubeSubscriptionEtagTable(
    @PrimaryKey
    @ColumnInfo(name = "offset") override val offset: Int,
    @ColumnInfo(name = "next_page_token") override val nextPageToken: String?,
    @ColumnInfo(name = "etag") override val eTag: String,
) : YouTubeSubscriptionQuery {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addSubscriptionEtag(etag: List<YouTubeSubscriptionEtagTable>)

        @Query("SELECT * FROM subscription_alphabetical_order_etag AS e WHERE `offset` = :offset")
        suspend fun findSubscriptionQuery(offset: Int): YouTubeSubscriptionEtagTable?

        @Query("DELETE FROM subscription_alphabetical_order_etag")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "subscription_relevance_order",
    foreignKeys = [
        ForeignKey(
            entity = YouTubeSubscriptionTable::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
        ),
    ],
)
internal class YouTubeSubscriptionRelevanceOrderTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "subscription_id")
    val id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subs_order", defaultValue = (-1).toString())
    val order: Int = -1,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addSubscriptionRelevanceOrders(order: Collection<YouTubeSubscriptionRelevanceOrderTable>)

        @Query("DELETE FROM subscription_relevance_order WHERE subscription_id IN (:id)")
        suspend fun removeSubscriptionsRelevanceOrdered(id: Collection<YouTubeSubscription.Id>)

        @Query("DELETE FROM subscription_relevance_order")
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
) : YouTubeSubscriptionRelevanceOrdered {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT s.*, c.title AS channel_title, c.icon AS channel_icon, o.subs_order, subs_order " +
                "FROM subscription AS s " +
                "INNER JOIN channel AS c ON c.id = s.channel_id " +
                "INNER JOIN subscription_relevance_order AS o ON s.id = o.subscription_id " +
                "ORDER BY subs_order ASC"
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
    @Embedded
    override val cacheControl: CacheControlDb,
) : YouTubeSubscriptionSummary {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT s.id AS subscription_id, s.channel_id, ca.uploaded_playlist_id," +
                " pe.fetched_at AS fetched_at, pe.max_age AS max_age FROM subscription AS s " +
                "LEFT OUTER JOIN yt_channel_related_playlist AS ca ON s.channel_id = ca.channel_id " +
                "LEFT OUTER JOIN playlist_expire AS pe ON pe.playlist_id = ca.uploaded_playlist_id " +
                "WHERE subscription_id IN (:ids)"
        )
        fun findSubscriptionSummaries(ids: Collection<YouTubeSubscription.Id>): List<YouTubeSubscriptionSummaryDb>

        @Query(
            "SELECT s.id AS subscription_id, s.channel_id, ca.uploaded_playlist_id," +
                " pe.fetched_at AS fetched_at, pe.max_age AS max_age FROM subscription AS s " +
                "INNER JOIN channel AS c ON s.channel_id = c.id " +
                "LEFT OUTER JOIN yt_channel_related_playlist AS ca ON s.channel_id = ca.channel_id " +
                "LEFT OUTER JOIN playlist_expire AS pe ON pe.playlist_id = ca.uploaded_playlist_id " +
                "ORDER BY LOWER(c.title) ASC " +
                "LIMIT :pageSize OFFSET :offset"
        )
        fun findSubscriptionSummariesByOffset(
            offset: Int,
            pageSize: Int,
        ): List<YouTubeSubscriptionSummaryDb>
    }
}

internal interface YouTubeSubscriptionDaoProviders {
    val youTubeSubscriptionDao: YouTubeSubscriptionTable.Dao
    val youTubeSubscriptionDbDao: YouTubeSubscriptionDb.Dao
    val youTubeSubscriptionEtagDao: YouTubeSubscriptionEtagTable.Dao
    val youTubeSubscriptionRelevanceOrderDao: YouTubeSubscriptionRelevanceOrderTable.Dao
    val youTubeSubscriptionSummaryDbDao: YouTubeSubscriptionSummaryDb.Dao
}

internal interface YouTubeSubscriptionDao : YouTubeSubscriptionTable.Dao,
    YouTubeSubscriptionDb.Dao, YouTubeSubscriptionEtagTable.Dao,
    YouTubeSubscriptionRelevanceOrderTable.Dao, YouTubeSubscriptionSummaryDb.Dao {
    suspend fun addSubscriptionEntities(subscriptions: Collection<YouTubeSubscription>)
    suspend fun addSubscriptionQuery(query: Collection<YouTubeSubscriptionQuery>)
    suspend fun removeSubscriptionEntities(id: Collection<YouTubeSubscription.Id>)
}

internal class YouTubeSubscriptionDaoImpl @Inject constructor(
    private val db: AppDatabase,
) : YouTubeSubscriptionDao, YouTubeSubscriptionTable.Dao by db.youTubeSubscriptionDao,
    YouTubeSubscriptionDb.Dao by db.youTubeSubscriptionDbDao,
    YouTubeSubscriptionEtagTable.Dao by db.youTubeSubscriptionEtagDao,
    YouTubeSubscriptionRelevanceOrderTable.Dao by db.youTubeSubscriptionRelevanceOrderDao,
    YouTubeSubscriptionSummaryDb.Dao by db.youTubeSubscriptionSummaryDbDao {
    override suspend fun addSubscriptionEntities(subscriptions: Collection<YouTubeSubscription>) =
        db.withTransaction {
            addSubscriptions(subscriptions.map { it.toDbEntity() })
            val orders = subscriptions.filterIsInstance<YouTubeSubscriptionRelevanceOrdered>()
                .map { YouTubeSubscriptionRelevanceOrderTable(it.id, it.order) }
            if (orders.isNotEmpty()) {
                addSubscriptionRelevanceOrders(orders)
            }
        }

    override suspend fun addSubscriptionQuery(query: Collection<YouTubeSubscriptionQuery>) {
        check(query.all { it.order == YouTubeSubscriptionQuery.Order.ALPHABETICAL })
        val t = query.map {
            YouTubeSubscriptionEtagTable(it.offset, it.nextPageToken, checkNotNull(it.eTag))
        }
        db.withTransaction { addSubscriptionEtag(t) }
    }

    override suspend fun removeSubscriptionEntities(id: Collection<YouTubeSubscription.Id>) =
        db.withTransaction {
            removeSubscriptionsRelevanceOrdered(id)
            removeSubscriptions(id)
        }

    override suspend fun deleteTable() = db.withTransaction {
        listOf(
            db.youTubeSubscriptionDao,
            db.youTubeSubscriptionEtagDao,
            db.youTubeSubscriptionRelevanceOrderDao,
        ).forEach { it.deleteTable() }
    }

    companion object {
        private fun YouTubeSubscription.toDbEntity(): YouTubeSubscriptionTable =
            YouTubeSubscriptionTable(
                id = id, subscribeSince = subscribeSince, channelId = channel.id,
            )
    }
}
