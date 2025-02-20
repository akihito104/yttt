package com.freshdigitable.yttt.data.source.local.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Query
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeSubscription
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.local.AppDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeLiveSubscription(
    @ColumnInfo(name = "id")
    private val _id: YouTubeSubscription.Id,
    @ColumnInfo(name = "subscription_since")
    override val subscribeSince: Instant,
    @ColumnInfo(name = "order")
    override val order: Int,
    @ColumnInfo(name = "channel_id")
    private val channelId: YouTubeChannel.Id,
    @ColumnInfo(name = "channel_title")
    private val channelTitle: String,
    @ColumnInfo(name = "channel_icon")
    private val iconUrl: String,
) : LiveSubscription {
    override val id: LiveSubscription.Id get() = _id.mapTo()
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = channelId.mapTo(),
            title = channelTitle,
            iconUrl = iconUrl,
            platform = YouTube,
        )

    @androidx.room.Dao
    interface Dao {
        @Query(
            "SELECT s.id AS id, s.subscription_since, s.subs_order AS `order`, " +
                "c.id AS channel_id, c.title AS channel_title, c.icon AS channel_icon FROM subscription AS s " +
                "INNER JOIN channel AS c ON c.id = s.channel_id ORDER BY subs_order ASC"
        )
        fun getSubscriptionPagingSource(): PagingSource<Int, YouTubeLiveSubscription>
    }
}

internal interface YouTubePageSourceDaoProviders {
    val youtubeLiveSubscription: YouTubeLiveSubscription.Dao
}

internal interface YouTubePageSourceDao : YouTubeLiveSubscription.Dao

@Singleton
internal class YouTubePageSourceDaoImpl @Inject constructor(
    db: YouTubePageSourceDaoProviders,
) : YouTubePageSourceDao, YouTubeLiveSubscription.Dao by db.youtubeLiveSubscription

interface YouTubePagingSource {
    fun getYouTubeLiveSubscriptionPageSource(): PagingSource<Int, YouTubeLiveSubscription>
}

@Singleton
internal class YouTubePagingSourceImpl @Inject constructor(
    private val db: AppDatabase,
) : YouTubePagingSource {
    override fun getYouTubeLiveSubscriptionPageSource(): PagingSource<Int, YouTubeLiveSubscription> {
        return db.youtubeLiveSubscription.getSubscriptionPagingSource()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubePagingSourceModule {
    @Binds
    fun bindYouTubePagingSource(impl: YouTubePagingSourceImpl): YouTubePagingSource
}
