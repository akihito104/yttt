package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.local.TableDeletable
import java.time.Instant
import javax.inject.Inject

@Entity(tableName = "twitch_user")
internal data class TwitchUserTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    override val id: TwitchUser.Id,
    @ColumnInfo(name = "login_name")
    override val loginName: String,
    @ColumnInfo(name = "display_name")
    override val displayName: String,
) : TwitchUser {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addUsers(user: Collection<TwitchUserTable>)

        @Query("SELECT * FROM twitch_user WHERE id = :id")
        suspend fun findUser(id: TwitchUser.Id): TwitchUserTable?

        @Query("DELETE FROM twitch_user")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "twitch_user_detail",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            childColumns = ["user_id"],
            parentColumns = ["id"],
        ),
    ],
)
internal class TwitchUserDetailTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "user_id")
    val id: TwitchUser.Id,
    @ColumnInfo(name = "profile_image_url")
    val profileImageUrl: String,
    @ColumnInfo(name = "views_count")
    val viewsCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo("description")
    val description: String,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addUserDetailEntities(details: Collection<TwitchUserDetailTable>)

        @Query("DELETE FROM twitch_user_detail")
        override suspend fun deleteTable()
    }
}

internal data class TwitchUserDetailDbView(
    @Embedded
    private val user: TwitchUserTable,
    @ColumnInfo("description")
    override val description: String,
    @ColumnInfo("profile_image_url")
    override val profileImageUrl: String,
    @ColumnInfo("views_count")
    override val viewsCount: Int,
    @ColumnInfo("created_at")
    override val createdAt: Instant,
) : TwitchUserDetail, TwitchUser by user {
    companion object {
        internal const val SQL_USER_DETAIL = "SELECT u.*, d.profile_image_url, d.views_count, " +
            "d.created_at, d.description FROM twitch_user_detail AS d " +
            "INNER JOIN twitch_user AS u ON d.user_id = u.id"
        internal const val SQL_EMBED_PREFIX = "u_"
        internal const val SQL_EMBED_ALIAS = "u.id AS ${SQL_EMBED_PREFIX}id, " +
            "u.display_name AS ${SQL_EMBED_PREFIX}display_name, u.login_name AS ${SQL_EMBED_PREFIX}login_name, " +
            "u.description AS ${SQL_EMBED_PREFIX}description, u.created_at AS ${SQL_EMBED_PREFIX}created_at, " +
            "u.views_count AS ${SQL_EMBED_PREFIX}views_count, u.profile_image_url AS ${SQL_EMBED_PREFIX}profile_image_url"
    }

    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT u.* FROM twitch_auth_user AS a " +
                "INNER JOIN (${SQL_USER_DETAIL}) AS u ON a.user_id = u.id LIMIT 1"
        )
        suspend fun findMe(): TwitchUserDetailDbView?

        @Query(
            "SELECT v.* FROM (SELECT * FROM (${SQL_USER_DETAIL}) WHERE id IN (:ids)) AS v " +
                "INNER JOIN (SELECT * FROM twitch_user_detail_expire WHERE :current < expired_at) AS e ON v.id = e.user_id"
        )
        suspend fun findUserDetail(
            ids: Collection<TwitchUser.Id>,
            current: Instant,
        ): List<TwitchUserDetailDbView>
    }
}

@Entity(
    tableName = "twitch_user_detail_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserDetailTable::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
        ),
    ],
)
internal class TwitchUserDetailExpireTable(
    @PrimaryKey
    @ColumnInfo("user_id", index = true)
    val userId: TwitchUser.Id,
    @ColumnInfo("expired_at", defaultValue = "null")
    val expiredAt: Instant? = null,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addUserDetailExpireEntities(expires: Collection<TwitchUserDetailExpireTable>)

        @Query("DELETE FROM twitch_user_detail_expire")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "twitch_broadcaster",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["follower_user_id"],
        ),
    ],
    primaryKeys = ["user_id", "follower_user_id"]
)
internal class TwitchBroadcasterTable(
    @ColumnInfo(name = "user_id")
    val id: TwitchUser.Id,
    @ColumnInfo(name = "follower_user_id", index = true)
    val followerId: TwitchUser.Id,
    @ColumnInfo(name = "followed_at")
    val followedAt: Instant,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addBroadcasterEntities(broadcasters: Collection<TwitchBroadcasterTable>)

        @Query("DELETE FROM twitch_broadcaster WHERE follower_user_id = :followerId")
        suspend fun removeBroadcastersByFollowerId(followerId: TwitchUser.Id)

        @Query("DELETE FROM twitch_broadcaster")
        override suspend fun deleteTable()
    }
}

@Entity(
    tableName = "twitch_broadcaster_expire",
    foreignKeys = [
        ForeignKey(
            entity = TwitchAuthorizedUserTable::class,
            parentColumns = ["user_id"],
            childColumns = ["follower_user_id"],
        ),
    ],
)
internal class TwitchBroadcasterExpireTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("follower_user_id", index = true)
    val followerId: TwitchUser.Id,
    @ColumnInfo("expire_at")
    val expireAt: Instant,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addBroadcasterExpireEntity(expires: TwitchBroadcasterExpireTable)

        @Query("DELETE FROM twitch_broadcaster_expire")
        override suspend fun deleteTable()
    }
}

internal data class TwitchBroadcasterDb(
    @Embedded
    private val user: TwitchUserTable,
    @ColumnInfo("followed_at")
    override val followedAt: Instant
) : TwitchBroadcaster, TwitchUser by user {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT u.*, b.followed_at FROM " +
                "(SELECT bb.* FROM twitch_broadcaster AS bb " +
                " INNER JOIN twitch_broadcaster_expire AS e ON e.follower_user_id = bb.follower_user_id " +
                " WHERE :current < e.expire_at) AS b " +
                "INNER JOIN twitch_user AS u ON b.user_id = u.id " +
                "WHERE b.follower_user_id = :id"
        )
        suspend fun findBroadcastersByFollowerId(
            id: TwitchUser.Id,
            current: Instant,
        ): List<TwitchBroadcasterDb>
    }
}

@Entity(
    tableName = "twitch_auth_user",
    foreignKeys = [
        ForeignKey(
            entity = TwitchUserTable::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
)
internal class TwitchAuthorizedUserTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo("user_id")
    val userId: TwitchUser.Id,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Insert
        suspend fun setMeEntity(me: TwitchAuthorizedUserTable)

        @Query("DELETE FROM twitch_auth_user")
        override suspend fun deleteTable()
    }
}

internal interface TwitchUserDaoProviders {
    val twitchUserDao: TwitchUserTable.Dao
    val twitchUserDetailDao: TwitchUserDetailTable.Dao
    val twitchBroadcasterDao: TwitchBroadcasterTable.Dao
    val twitchUserDetailExpireDao: TwitchUserDetailExpireTable.Dao
    val twitchBroadcasterExpireDao: TwitchBroadcasterExpireTable.Dao
    val twitchAuthUserDao: TwitchAuthorizedUserTable.Dao
    val twitchBroadcasterDbDao: TwitchBroadcasterDb.Dao
    val twitchUserDetailViewDao: TwitchUserDetailDbView.Dao
}

internal interface TwitchUserDao : TwitchUserTable.Dao, TwitchUserDetailTable.Dao,
    TwitchUserDetailExpireTable.Dao, TwitchBroadcasterTable.Dao, TwitchBroadcasterExpireTable.Dao,
    TwitchAuthorizedUserTable.Dao, TwitchBroadcasterDb.Dao, TwitchUserDetailDbView.Dao

internal class TwitchUserDaoImpl @Inject constructor(
    private val db: TwitchUserDaoProviders
) : TwitchUserDao, TwitchUserTable.Dao by db.twitchUserDao,
    TwitchUserDetailTable.Dao by db.twitchUserDetailDao,
    TwitchUserDetailExpireTable.Dao by db.twitchUserDetailExpireDao,
    TwitchBroadcasterTable.Dao by db.twitchBroadcasterDao,
    TwitchBroadcasterExpireTable.Dao by db.twitchBroadcasterExpireDao,
    TwitchAuthorizedUserTable.Dao by db.twitchAuthUserDao,
    TwitchBroadcasterDb.Dao by db.twitchBroadcasterDbDao,
    TwitchUserDetailDbView.Dao by db.twitchUserDetailViewDao {
    override suspend fun deleteTable() {
        listOf(
            db.twitchUserDao,
            db.twitchUserDetailDao,
            db.twitchUserDetailExpireDao,
            db.twitchBroadcasterDao,
            db.twitchBroadcasterExpireDao,
            db.twitchAuthUserDao,
        ).forEach { it.deleteTable() }
    }
}
