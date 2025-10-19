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
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceTestRule
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
        fun setup() = rule.runWithScope {
            localSource.setMe(me.toUpdatable(CacheControl.zero()))
            localSource.replaceAllFollowings(followings(me.id, listOf(broadcaster(broadcaster))))
            localSource.addUsers(listOf(broadcaster.toUpdatable(CacheControl.zero())))
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = schedule.toUpdatable<TwitchChannelSchedule?>(CacheControl.zero())
            dao.replaceChannelScheduleEntities(broadcaster.id, updatable)
            // verify
            dao.findStreamScheduleEntity(streamSchedule.id).shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() = rule.runWithScope {
            // exercise
            extendedSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_removedAfterUnfollowing() = rule.runWithScope {
            // setup
            localSource.replaceAllFollowings(followings(me.id, emptyList()))
            // exercise
            extendedSource.cleanUpByUserId(listOf(broadcaster.id))
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
        fun setup() = rule.runWithScope {
            mapOf(
                me to listOf(broadcaster(broadcaster)),
                me2 to listOf(broadcaster(broadcaster), broadcaster(me)),
            ).forEach { (me, broadcasters) ->
                localSource.setMe(me.toUpdatable(CacheControl.zero()))
                localSource.replaceAllFollowings(followings(me.id, broadcasters))
            }
            val schedule = channelSchedule(listOf(streamSchedule), broadcaster)
            val updatable = schedule.toUpdatable<TwitchChannelSchedule?>(CacheControl.zero())
            dao.replaceChannelScheduleEntities(broadcaster.id, updatable)
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity?.id shouldBe streamSchedule.id
        }

        @Test
        fun cleanUpByUserId_cannotRemoveFollowingBroadcaster() = rule.runWithScope {
            // exercise
            extendedSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_cannotRemoveScheduleOfFollowedBroadcaster() = rule.runWithScope {
            // setup
            localSource.replaceAllFollowings(followings(me.id, emptyList()))
            // exercise
            extendedSource.cleanUpByUserId(listOf(broadcaster.id))
            // verify
            val entity = dao.findStreamScheduleEntity(streamSchedule.id)
            entity.shouldNotBeNull()
            val user = dao.findUser(broadcaster.id)
            user.shouldNotBeNull()
        }

        @Test
        fun cleanUpByUserId_removedSchedule() = rule.runWithScope {
            // setup
            listOf(me, me2).forEach {
                localSource.replaceAllFollowings(followings(it.id, emptyList()))
            }
            // exercise
            extendedSource.cleanUpByUserId(listOf(broadcaster.id, me2.id))
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

internal fun channelSchedule(
    segments: List<TwitchChannelSchedule.Stream>,
    broadcaster: TwitchUser,
    vacation: TwitchChannelSchedule.Vacation? = null,
): TwitchChannelSchedule = object : TwitchChannelSchedule {
    override val segments: List<TwitchChannelSchedule.Stream> get() = segments
    override val broadcaster: TwitchUser get() = broadcaster
    override val vacation: TwitchChannelSchedule.Vacation? get() = vacation
}

internal fun streamSchedule(
    id: String,
    startTime: Instant = Instant.EPOCH,
    duration: Duration = Duration.ofHours(3),
    category: TwitchCategory? = null,
    title: String = "title",
): TwitchChannelSchedule.Stream = object : TwitchChannelSchedule.Stream {
    override val id: TwitchChannelSchedule.Stream.Id get() = TwitchChannelSchedule.Stream.Id(id)
    override val startTime: Instant get() = startTime
    override val endTime: Instant? get() = startTime + duration
    override val title: String get() = title
    override val canceledUntil: String? get() = null
    override val category: TwitchCategory? get() = category
    override val isRecurring: Boolean get() = true
}

internal fun followings(
    followerId: TwitchUser.Id,
    followings: List<TwitchBroadcaster>,
    fetchedAt: Instant = Instant.EPOCH,
): Updatable<TwitchFollowings> =
    TwitchFollowings.create(followerId, followings, CacheControl.create(fetchedAt, Duration.ZERO))

internal fun broadcaster(
    user: TwitchUser,
    followedAt: Instant = Instant.EPOCH,
): TwitchBroadcaster = object : TwitchBroadcaster, TwitchUser by user {
    override val followedAt: Instant get() = followedAt
}
