package com.freshdigitable.yttt.data.source.local.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.Query
import androidx.room.withTransaction
import com.freshdigitable.yttt.data.model.LiveChannel
import com.freshdigitable.yttt.data.model.LiveChannelEntity
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.mapTo
import com.freshdigitable.yttt.data.source.local.AppDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TwitchLiveSubscription(
    @ColumnInfo(name = "follower_user_id")
    private val _id: TwitchUser.Id,
    @ColumnInfo(name = "followed_at")
    override val subscribeSince: Instant,
    @ColumnInfo(name = "channel_id")
    private val channelId: TwitchUser.Id,
    @ColumnInfo(name = "channel_name")
    private val channelName: String,
    @ColumnInfo(name = "channel_icon")
    private val channelIconUrl: String?,
) : LiveSubscription {
    override val id: LiveSubscription.Id
        get() = _id.mapTo()
    override val channel: LiveChannel
        get() = LiveChannelEntity(
            id = channelId.mapTo(),
            title = channelName,
            iconUrl = channelIconUrl ?: "",
            platform = Twitch,
        )

    @Ignore
    override val order: Int = 0 // TODO

    @androidx.room.Dao
    interface Dao {
        @Query(
            value = "SELECT b.follower_user_id, u.id AS channel_id, b.followed_at, u.display_name AS channel_name," +
                " ud.profile_image_url AS channel_icon FROM twitch_broadcaster AS b " +
                "INNER JOIN twitch_user AS u ON b.user_id = u.id " +
                "LEFT OUTER JOIN twitch_user_detail AS ud ON ud.user_id = u.id"
        )
        fun getTwitchLiveSubscriptionPagingSource(): PagingSource<Int, TwitchLiveSubscription>
    }
}

internal interface TwitchPageSourceDaoProviders {
    val twitchLiveSubscription: TwitchLiveSubscription.Dao
}

internal interface TwitchPageSourceDao : TwitchLiveSubscription.Dao

@Singleton
internal class TwitchPageSourceDaoImpl @Inject constructor(
    db: TwitchPageSourceDaoProviders,
) : TwitchPageSourceDao, TwitchLiveSubscription.Dao by db.twitchLiveSubscription

interface TwitchPagingSource {
    fun getTwitchLiveSubscriptionPagingSource(): PagingSource<Int, TwitchLiveSubscription>
    suspend fun isUpdatable(current: Instant): Boolean
}

@Singleton
internal class TwitchPagingSourceImpl @Inject constructor(
    private val db: AppDatabase,
) : TwitchPagingSource {
    override fun getTwitchLiveSubscriptionPagingSource(): PagingSource<Int, TwitchLiveSubscription> =
        db.twitchLiveSubscription.getTwitchLiveSubscriptionPagingSource()

    override suspend fun isUpdatable(current: Instant): Boolean = db.withTransaction {
        val users = db.twitchAuthUserDao.fetchAllUsers()
        for (u in users) {
            val expire = db.twitchBroadcasterExpireDao.findByFollowerUserId(u.userId)
                ?: return@withTransaction true
            if (expire.expireAt <= current) return@withTransaction true
        }
        return@withTransaction false
    }
}

@InstallIn(SingletonComponent::class)
@Module
internal interface TwitchPagingSourceModule {
    @Binds
    fun bindTwitchPagingSource(impl: TwitchPagingSourceImpl): TwitchPagingSource
}
