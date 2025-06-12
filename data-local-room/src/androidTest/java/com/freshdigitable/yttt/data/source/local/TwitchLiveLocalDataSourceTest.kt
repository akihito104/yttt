package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchChannelScheduleUpdatable
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.db.DataSourceTestRule
import com.freshdigitable.yttt.data.source.local.db.DateTimeProviderFake
import com.freshdigitable.yttt.data.source.local.db.NopImageDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class TwitchLiveLocalDataSourceTest {
    class SingleAccount {
        @get:Rule
        internal val rule = TwitchDataSourceTestRule()
        private val me = userDetail(id = "user_me")
        private val broadcaster = userDetail(id = "broadcaster")
        private val streamSchedule = streamSchedule("stream_id")

        @Before
        fun setup() = rule.runWithLocalSource {
            dataSource.setMe(me)
            dataSource.replaceAllFollowings(followings(me.id, listOf(broadcaster(broadcaster))))
            dataSource.addUsers(listOf(broadcaster))
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = TwitchChannelScheduleUpdatable.createAtFetched(schedule, Instant.EPOCH)
            dao.replaceChannelSchedules(broadcaster.id, updatable)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity?.id).isEqualTo(streamSchedule.id)
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() =
            rule.runWithLocalSource {
                // exercise
                dataSource.cleanUpByUserId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
                val user = dao.findUser(broadcaster.id)
                assertThat(user).isNotNull()
            }

        @Test
        fun cleanUpByUserId_removedAfterUnfollowing() =
            rule.runWithLocalSource {
                // setup
                dataSource.replaceAllFollowings(followings(me.id, emptyList()))
                // exercise
                dataSource.cleanUpByUserId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNull()
                val user = dao.findUser(broadcaster.id)
                assertThat(user).isNull()
            }
    }

    class MultiAccount {
        @get:Rule
        internal val rule = TwitchDataSourceTestRule()
        private val me1 = userDetail(id = "user_me")
        private val me2 = userDetail(id = "user_me2")
        private val broadcaster = userDetail(id = "broadcaster")
        private val streamSchedule = streamSchedule("stream_id")

        @Before
        fun setup() = rule.runWithLocalSource {
            mapOf(
                me1 to listOf(broadcaster(broadcaster)),
                me2 to listOf(broadcaster(broadcaster), broadcaster(me1))
            ).forEach { (me, broadcasters) ->
                dataSource.setMe(me)
                dataSource.replaceAllFollowings(followings(me.id, broadcasters))
            }
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = TwitchChannelScheduleUpdatable.createAtFetched(schedule, Instant.EPOCH)
            dao.replaceChannelSchedules(broadcaster.id, updatable)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity?.id).isEqualTo(streamSchedule.id)
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() =
            rule.runWithLocalSource {
                // exercise
                dataSource.cleanUpByUserId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
                val user = dao.findUser(broadcaster.id)
                assertThat(user).isNotNull()
            }

        @Test
        fun cleanUpByUserId_cannotRemoveScheduleOfFollowedBroadcaster() =
            rule.runWithLocalSource {
                // setup
                dataSource.replaceAllFollowings(followings(me1.id, emptyList()))
                // exercise
                dataSource.cleanUpByUserId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
                val user = dao.findUser(broadcaster.id)
                assertThat(user).isNotNull()
            }

        @Test
        fun cleanUpByUserId_removedSchedule() = rule.runWithLocalSource {
            // setup
            listOf(me1, me2).forEach {
                dataSource.replaceAllFollowings(followings(it.id, emptyList()))
            }
            // exercise
            dataSource.cleanUpByUserId(listOf(broadcaster.id, me2.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity).isNull()
            val user = dao.findUser(broadcaster.id)
            assertThat(user).isNull()
            val userMe2 = dao.findUser(me2.id)
            assertThat(userMe2).isNotNull()
        }
    }
}

internal class TwitchDataSourceTestRule(
    baseTime: Instant = Instant.EPOCH,
) : DataSourceTestRule<TwitchDao, TwitchLocalDataSource>(baseTime) {
    override fun createDao(database: AppDatabase): TwitchDao = TwitchDao(
        database,
        TwitchUserDaoImpl(database),
        TwitchScheduleDaoImpl(database),
        TwitchStreamDaoImpl(database),
    )

    override fun createTestScope(testScope: TestScope): DatabaseTestScope<TwitchDao, TwitchLocalDataSource> {
        val dataSource = TwitchLocalDataSource(
            dao, dateTimeProvider,
            IoScope(StandardTestDispatcher(testScope.testScheduler)), NopImageDataSource
        )
        return object : DatabaseTestScope<TwitchDao, TwitchLocalDataSource> {
            override val testScope: TestScope get() = testScope
            override val dateTimeProvider: DateTimeProviderFake get() = this@TwitchDataSourceTestRule.dateTimeProvider
            override val dao: TwitchDao get() = this@TwitchDataSourceTestRule.dao
            override val dataSource: TwitchLocalDataSource get() = dataSource
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
    override val fetchedAt: Instant get() = Instant.EPOCH
    override val maxAge: Duration get() = Duration.ZERO
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
): TwitchFollowings = TwitchFollowings.createAtFetched(followerId, followings, fetchedAt)

private fun broadcaster(
    user: TwitchUser,
    followedAt: Instant = Instant.EPOCH,
): TwitchBroadcaster = object : TwitchBroadcaster, TwitchUser by user {
    override val followedAt: Instant get() = followedAt
}
