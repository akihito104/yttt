package com.freshdigitable.yttt.data.source.local.db

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.Updatable
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

        @Query("DELETE FROM twitch_user WHERE id IN (:id)")
        suspend fun removeUsers(id: Collection<TwitchUser.Id>)

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
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo("description")
    val description: String,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addUserDetailEntities(details: Collection<TwitchUserDetailTable>)

        @Query("DELETE FROM twitch_user_detail WHERE user_id IN (:id)")
        suspend fun removeUserDetail(id: Collection<TwitchUser.Id>)

        @Query("DELETE FROM twitch_user_detail")
        override suspend fun deleteTable()
    }
}

@DatabaseView(
    value = "SELECT u.login_name, u.display_name, d.* FROM twitch_user_detail AS d " +
        "INNER JOIN twitch_user AS u ON d.user_id = u.id",
    viewName = "twitch_user_detail_view",
)
internal data class TwitchUserDetailDbView(
    @Embedded private val detail: TwitchUserDetailTable,
    @ColumnInfo("login_name") override val loginName: String,
    @ColumnInfo("display_name") override val displayName: String,
) : TwitchUserDetail {
    override val id: TwitchUser.Id get() = detail.id
    override val profileImageUrl: String get() = detail.profileImageUrl
    override val createdAt: Instant get() = detail.createdAt
    override val description: String get() = detail.description
}

internal data class TwitchUserDetailDbUpdatable(
    @Embedded override val item: TwitchUserDetailDbView,
    @Embedded override val cacheControl: CacheControlDb,
) : Updatable<TwitchUserDetail> {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT u.*, e.fetched_at, e.max_age FROM twitch_auth_user AS a " +
                "INNER JOIN twitch_user_detail_view AS u ON a.user_id = u.user_id " +
                "LEFT OUTER JOIN twitch_user_detail_expire AS e ON u.user_id = e.user_id " +
                "LIMIT 1"
        )
        suspend fun findMe(): TwitchUserDetailDbUpdatable?

        @Query(
            "SELECT v.*, e.fetched_at, e.max_age FROM (SELECT * FROM twitch_user_detail_view WHERE user_id IN (:ids)) AS v " +
                "LEFT OUTER JOIN twitch_user_detail_expire AS e ON v.user_id = e.user_id"
        )
        suspend fun findUserDetail(ids: Collection<TwitchUser.Id>): List<TwitchUserDetailDbUpdatable>
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
    @Embedded val cacheControl: CacheControlDb,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addUserDetailExpireEntities(expires: Collection<TwitchUserDetailExpireTable>)

        @Query("DELETE FROM twitch_user_detail_expire WHERE user_id IN (:ids)")
        suspend fun removeDetailExpireEntities(ids: Collection<TwitchUser.Id>)

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

        @Query(
            "SELECT b.user_id AS user_id, COUNT(follower_user_id) > 0 AS is_followed " +
                "FROM twitch_broadcaster AS b WHERE user_id IN (:ids) " +
                "GROUP BY user_id"
        )
        suspend fun isBroadcasterFollowed(ids: Collection<TwitchUser.Id>):
            Map<@MapColumn("user_id") TwitchUser.Id, @MapColumn("is_followed") Boolean>

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
    @Embedded val cacheControl: CacheControlDb,
) {
    @androidx.room.Dao
    internal interface Dao : TableDeletable {
        @Upsert
        suspend fun addBroadcasterExpireEntity(expires: TwitchBroadcasterExpireTable)

        @Query("SELECT * FROM twitch_broadcaster_expire WHERE follower_user_id = :id")
        suspend fun findByFollowerUserId(id: TwitchUser.Id): TwitchBroadcasterExpireTable?

        @Query("DELETE FROM twitch_broadcaster_expire")
        override suspend fun deleteTable()
    }
}

internal data class TwitchBroadcasterDb(
    @Embedded
    private val user: TwitchUserTable,
    @ColumnInfo("followed_at")
    override val followedAt: Instant,
) : TwitchBroadcaster, TwitchUser by user {
    @androidx.room.Dao
    internal interface Dao {
        @Query(
            "SELECT u.*, b.followed_at FROM twitch_broadcaster AS b " +
                "INNER JOIN twitch_user AS u ON b.user_id = u.id " +
                "WHERE b.follower_user_id = :id"
        )
        suspend fun findBroadcastersByFollowerId(id: TwitchUser.Id): List<TwitchBroadcasterDb>
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

        @Query("SELECT * FROM twitch_auth_user WHERE user_id IN (:ids)")
        suspend fun findAuthorizedUser(ids: Collection<TwitchUser.Id>): List<TwitchAuthorizedUserTable>

        @Query("SELECT * FROM twitch_auth_user")
        suspend fun fetchAllUsers(): List<TwitchAuthorizedUserTable>

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
    val twitchUserDetailViewDao: TwitchUserDetailDbUpdatable.Dao
}

internal interface TwitchUserDao : TwitchUserTable.Dao, TwitchUserDetailTable.Dao,
    TwitchUserDetailExpireTable.Dao, TwitchBroadcasterTable.Dao, TwitchBroadcasterExpireTable.Dao,
    TwitchAuthorizedUserTable.Dao, TwitchBroadcasterDb.Dao, TwitchUserDetailDbUpdatable.Dao

internal class TwitchUserDaoImpl @Inject constructor(
    private val db: TwitchUserDaoProviders
) : TwitchUserDao, TwitchUserTable.Dao by db.twitchUserDao,
    TwitchUserDetailTable.Dao by db.twitchUserDetailDao,
    TwitchUserDetailExpireTable.Dao by db.twitchUserDetailExpireDao,
    TwitchBroadcasterTable.Dao by db.twitchBroadcasterDao,
    TwitchBroadcasterExpireTable.Dao by db.twitchBroadcasterExpireDao,
    TwitchAuthorizedUserTable.Dao by db.twitchAuthUserDao,
    TwitchBroadcasterDb.Dao by db.twitchBroadcasterDbDao,
    TwitchUserDetailDbUpdatable.Dao by db.twitchUserDetailViewDao {
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
