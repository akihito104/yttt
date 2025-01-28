package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.TwitchBroadcaster
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
import com.freshdigitable.yttt.data.source.local.db.DatabaseTestRule
import com.freshdigitable.yttt.data.source.local.db.DateTimeProviderFake
import com.freshdigitable.yttt.data.source.local.db.NopImageDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl
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
        internal val rule = TwitchDatabaseTestRule()
        private val me = userDetail(id = "user_me")
        private val broadcaster = userDetail(id = "broadcaster")
        private val streamSchedule = streamSchedule("stream_id")

        @Before
        fun setup() = rule.runWithLocalSource {
            dataSource.setMe(me)
            dataSource.replaceAllFollowings(followings(me.id, listOf(broadcaster(broadcaster))))
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            dao.replaceChannelSchedules(listOf(schedule), Instant.EPOCH)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity?.id).isEqualTo(streamSchedule.id)
        }

        @Test
        fun removeChannelSchedulesByBroadcasterId_cannotRemoveFollowingBroadcaster() =
            rule.runWithLocalSource {
                // exercise
                dataSource.removeChannelSchedulesByBroadcasterId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
            }

        @Test
        fun removeChannelSchedulesByBroadcasterId_removedAfterUnfollowing() =
            rule.runWithLocalSource {
                // setup
                dataSource.replaceAllFollowings(followings(me.id, emptyList()))
                // exercise
                dataSource.removeChannelSchedulesByBroadcasterId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNull()
            }
    }

    class MultiAccount {
        @get:Rule
        internal val rule = TwitchDatabaseTestRule()
        private val me = listOf(userDetail(id = "user_me"), userDetail(id = "user_me2"))
        private val broadcaster = userDetail(id = "broadcaster")
        private val streamSchedule = streamSchedule("stream_id")

        @Before
        fun setup() = rule.runWithLocalSource {
            me.forEach {
                dataSource.setMe(it)
                dataSource.replaceAllFollowings(followings(it.id, listOf(broadcaster(broadcaster))))
            }
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            dao.replaceChannelSchedules(listOf(schedule), Instant.EPOCH)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity?.id).isEqualTo(streamSchedule.id)
        }

        @Test
        fun removeChannelSchedulesByBroadcasterId_cannotRemoveFollowingBroadcaster() =
            rule.runWithLocalSource {
                // exercise
                dataSource.removeChannelSchedulesByBroadcasterId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
            }

        @Test
        fun removeChannelSchedulesByBroadcasterId_cannotRemoveScheduleOfFollowedBroadcaster() =
            rule.runWithLocalSource {
                // setup
                dataSource.replaceAllFollowings(followings(me[0].id, emptyList()))
                // exercise
                dataSource.removeChannelSchedulesByBroadcasterId(listOf(broadcaster.id))
                // verify
                val entity = dao.findStreamScheduleEntity(streamSchedule.id)
                assertThat(entity).isNotNull()
            }

        @Test
        fun removeChannelSchedulesByBroadcasterId_removedSchedule() = rule.runWithLocalSource {
            // setup
            me.forEach {
                dataSource.replaceAllFollowings(followings(it.id, emptyList()))
            }
            // exercise
            dataSource.removeChannelSchedulesByBroadcasterId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            assertThat(entity).isNull()
        }
    }
}

internal class TwitchDatabaseTestRule(
    baseTime: Instant = Instant.EPOCH,
) : DatabaseTestRule<TwitchDao, TwitchLiveLocalDataSource>(baseTime) {
    override fun createDao(database: AppDatabase): TwitchDao = TwitchDao(
        database,
        TwitchUserDaoImpl(database),
        TwitchScheduleDaoImpl(database),
        TwitchStreamDaoImpl(database),
    )

    override fun createTestScope(testScope: TestScope): DatabaseTestScope<TwitchDao, TwitchLiveLocalDataSource> {
        val dataSource = TwitchLiveLocalDataSource(dao, dateTimeProvider, NopImageDataSource)
        return object : DatabaseTestScope<TwitchDao, TwitchLiveLocalDataSource> {
            override val testScope: TestScope get() = testScope
            override val dateTimeProvider: DateTimeProviderFake get() = this@TwitchDatabaseTestRule.dateTimeProvider
            override val dao: TwitchDao get() = this@TwitchDatabaseTestRule.dao
            override val dataSource: TwitchLiveLocalDataSource get() = dataSource
        }
    }
}

private fun userDetail(
    id: String,
): TwitchUserDetail = object : TwitchUserDetail {
    override val id: TwitchUser.Id get() = TwitchUser.Id(id)
    override val loginName: String get() = id
    override val displayName: String get() = loginName
    override val description: String get() = ""
    override val profileImageUrl: String get() = ""
    override val viewsCount: Int get() = 100
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
    override val category: TwitchChannelSchedule.StreamCategory? get() = null
    override val isRecurring: Boolean get() = true
}

private fun followings(
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
