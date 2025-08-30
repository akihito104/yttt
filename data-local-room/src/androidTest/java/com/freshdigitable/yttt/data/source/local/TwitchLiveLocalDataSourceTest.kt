package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.db.DataSourceTestRule
import com.freshdigitable.yttt.data.source.local.db.NopImageDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl
import com.freshdigitable.yttt.test.zero
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class TwitchLiveLocalDataSourceTest {
    private companion object {
        private val me = userDetail(id = "user_me")
        private val broadcaster = userDetail(id = "broadcaster")
        private val streamSchedule = streamSchedule("stream_id")
    }

    class SingleAccount {
        @get:Rule
        internal val rule = TwitchDataSourceTestRule()

        @Before
        fun setup() = rule.runWithLocalSource {
            dataSource.setMe(me.toUpdatable(CacheControl.zero()))
            dataSource.replaceAllFollowings(followings(me.id, listOf(broadcaster(broadcaster))))
            dataSource.addUsers(listOf(broadcaster.toUpdatable(CacheControl.zero())))
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = schedule.toUpdatable<TwitchChannelSchedule?>(CacheControl.zero())
            dao.replaceChannelSchedules(broadcaster.id, updatable)
            // verify
            dao.findStreamScheduleEntity(streamSchedule.id).shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() = rule.runWithLocalSource {
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_removedAfterUnfollowing() = rule.runWithLocalSource {
            // setup
            dataSource.replaceAllFollowings(followings(me.id, emptyList()))
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldBeNull()
        }
    }

    class MultiAccount {
        @get:Rule
        internal val rule = TwitchDataSourceTestRule()
        private val me2 = userDetail(id = "user_me2")

        @Before
        fun setup() = rule.runWithLocalSource {
            mapOf(
                me to listOf(broadcaster(broadcaster)),
                me2 to listOf(broadcaster(broadcaster), broadcaster(me))
            ).forEach { (me, broadcasters) ->
                dataSource.setMe(me.toUpdatable(CacheControl.zero()))
                dataSource.replaceAllFollowings(followings(me.id, broadcasters))
            }
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = schedule.toUpdatable<TwitchChannelSchedule?>(CacheControl.zero())
            dao.replaceChannelSchedules(broadcaster.id, updatable)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity?.id shouldBe streamSchedule.id
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() = rule.runWithLocalSource {
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_cannotRemoveScheduleOfFollowedBroadcaster() = rule.runWithLocalSource {
            // setup
            dataSource.replaceAllFollowings(followings(me.id, emptyList()))
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_removedSchedule() = rule.runWithLocalSource {
            // setup
            listOf(me, me2).forEach {
                dataSource.replaceAllFollowings(followings(it.id, emptyList()))
            }
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id, me2.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldBeNull()
            val userMe2 = dao.findUser(me2.id)
            userMe2.shouldNotBeNull()
        }
    }
}

internal fun userDetail(
    id: String,
): TwitchUserDetail = object : TwitchUserDetail {
    override val id: TwitchUser.Id get() = TwitchUser.Id(id)
    override val loginName: String get() = id
    override val displayName: String get() = loginName
    override val description: String get() = ""
    override val profileImageUrl: String get() = ""
    override val createdAt: Instant get() = Instant.EPOCH
}

private fun channelSchedule(
    segments: List<TwitchChannelSchedule.Stream>,
    broadcaster: TwitchUser,
    vacation: TwitchChannelSchedule.Vacation? = null,
): TwitchChannelSchedule = object : TwitchChannelSchedule {
    override val segments: List<TwitchChannelSchedule.Stream> get() = segments
    override val broadcaster: TwitchUser get() = broadcaster
    override val vacation: TwitchChannelSchedule.Vacation? get() = vacation
}

private fun streamSchedule(
    id: String,
    startTime: Instant = Instant.EPOCH,
    duration: Duration = Duration.ofHours(3),
): TwitchChannelSchedule.Stream = object : TwitchChannelSchedule.Stream {
    override val id: TwitchChannelSchedule.Stream.Id get() = TwitchChannelSchedule.Stream.Id(id)
    override val startTime: Instant get() = startTime
    override val endTime: Instant? get() = startTime + duration
    override val title: String get() = "title"
    override val canceledUntil: String? get() = null
    override val category: TwitchCategory? get() = null
    override val isRecurring: Boolean get() = true
}

internal fun followings(
    followerId: TwitchUser.Id,
    followings: List<TwitchBroadcaster>,
    fetchedAt: Instant = Instant.EPOCH,
): Updatable<TwitchFollowings> =
    TwitchFollowings.create(followerId, followings, CacheControl.create(fetchedAt, Duration.ZERO))

private fun broadcaster(
    user: TwitchUser,
    followedAt: Instant = Instant.EPOCH,
): TwitchBroadcaster = object : TwitchBroadcaster, TwitchUser by user {
    override val followedAt: Instant get() = followedAt
}

internal class TwitchDataSourceTestRule : DataSourceTestRule<TwitchDao, TwitchLocalDataSource>() {
    override fun createDao(database: AppDatabase): TwitchDao = TwitchDao(
        database,
        TwitchUserDaoImpl(database),
        TwitchScheduleDaoImpl(database),
        TwitchStreamDaoImpl(database),
    )

    override fun createLocalSource(ioScope: IoScope): TwitchLocalDataSource =
        TwitchLocalDataSource(dao, ioScope, NopImageDataSource)
}
