package com.freshdigitable.yttt.data.source.local.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.source.local.YouTubeVideoEntity
import com.freshdigitable.yttt.data.source.local.broadcaster
import com.freshdigitable.yttt.data.source.local.channelSchedule
import com.freshdigitable.yttt.data.source.local.fixture.LiveDataSourceTestRule
import com.freshdigitable.yttt.data.source.local.followings
import com.freshdigitable.yttt.data.source.local.streamSchedule
import com.freshdigitable.yttt.data.source.local.userDetail
import com.freshdigitable.yttt.test.fromRemote
import com.freshdigitable.yttt.test.testWithRefresh
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldBeEmpty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(Enclosed::class)
abstract class LiveVideoUpcomingItemDaoTestImpl {
    private companion object {
        private val channels = listOf(
            YouTubeChannelTable(YouTubeChannel.Id("channel-0")),
            YouTubeChannelTable(YouTubeChannel.Id("channel-1")),
        )
        private val me = userDetail(id = "user_me")
        private val users = listOf(
            userDetail(id = "broadcaster-0"),
            userDetail(id = "broadcaster-1"),
        )
        private val broadcasters = users.map { broadcaster(it) }
        private val now = Instant.parse("2020-01-01T00:00:00Z")
        private val cacheControl = CacheControl.fromRemote(now)
    }

    @RunWith(AndroidJUnit4::class)
    abstract class InitImpl {
        @get:Rule
        internal val rule = LiveDataSourceTestRule()

        @Before
        fun setup() = rule.runWithScope {
            youtube.apply {
                dao.addChannelEntities(channels)
            }
            twitch.apply {
                localSource.setMe(me.toUpdatable(cacheControl))
                localSource.replaceAllFollowings(followings(me.id, broadcasters))
                localSource.addUsers(users.map { it.toUpdatable(cacheControl) })
            }
        }

        @Test
        fun init() = rule.runWithScope {
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                data.shouldBeEmpty()
            }
        }

        @Test
        fun anyUpcomingItemsAreNotIncluded() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.liveStreaming(channel = channels[0]),
                    YouTubeVideoEntity.archivedStream(channel = channels[0]),
                    YouTubeVideoEntity.uploadedVideo(channel = channels[0]),
                    YouTubeVideoEntity.freeChat(channel = channels[0]),
                    YouTubeVideoEntity.unscheduledUpcoming(channel = channels[0]), // should have startScheduleDateTime
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(id = "stream_id-0-0", userDetail = users[0], startedAt = now - 2.hours),
                    ),
                    cacheControl = CacheControl.fromRemote(now),
                )
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.shouldBeEmpty()
            }
        }

        @Test
        fun itemHasScheduledStartDateTimeOutOfPublicationTermIsNotIncluded() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(
                        channel = channels[0],
                        scheduledStartDateTime = now - (6.hours + 1.seconds),
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-0", startTime = now - (6.hours + 1.seconds)),
                    streamSchedule(id = "stream-1", startTime = now + (7.days + 1.seconds)),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.shouldBeEmpty()
            }
        }

        @Test
        fun imageUrlIsReplacedWithSize() = rule.runWithScope {
            // setup
            twitch.apply {
                val segments = listOf(
                    streamSchedule(
                        id = "stream-0",
                        startTime = now + 2.days,
                        category = category(id = "category-1", artUrlBase = "https://example.com/{width}x{height}"),
                    ),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data shouldHaveSize 1
                data.first().isLandscape.shouldBeFalse()
                data.first().thumbnailUrl shouldBeEqual "https://example.com/240x360"
            }
        }

        @Test
        fun scheduleCategoryIsNullThenImageUrlIsEmpty() = rule.runWithScope {
            // setup
            twitch.apply {
                val segments = listOf(
                    streamSchedule(
                        id = "stream-0",
                        startTime = now + 2.days,
                        category = null,
                    ),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data shouldHaveSize 1
                data.first().isLandscape.shouldBeFalse()
                data.first().thumbnailUrl.shouldBeEmpty()
            }
        }
    }

    @RunWith(AndroidJUnit4::class)
    abstract class SortingImpl {
        @get:Rule
        internal val rule = LiveDataSourceTestRule()

        @Before
        fun setup() = rule.runWithScope {
            youtube.apply {
                dao.addChannelEntities(channels)
            }
            twitch.apply {
                localSource.setMe(me.toUpdatable(cacheControl))
                localSource.replaceAllFollowings(followings(me.id, broadcasters))
                localSource.addUsers(users.map { it.toUpdatable(cacheControl) })
            }
        }

        @Test
        fun youtube_sortByDatetimeAscendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = now,
                    ),
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = now - 6.hours,
                    ),
                )
                dao.addVideoEntities(videos)
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-0-0", "video-1-0")
            }
        }

        @Test
        fun youtube_sortByTitleAscendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val scheduledStartDateTime = now + 2.hours
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = scheduledStartDateTime,
                        title = "title0",
                    ),
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = scheduledStartDateTime,
                        title = "title1",
                    ),
                )
                dao.addVideoEntities(videos)
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-1-0", "video-0-0")
            }
        }

        @Test
        fun twitch_sortByDatetimeAscendancy() = rule.runWithScope {
            // setup
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-0", startTime = now + 2.days),
                    streamSchedule(id = "stream-1", startTime = now + 3.days),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("stream-0", "stream-1")
            }
        }

        @Test
        fun twitch_sortByTitleAscendancy() = rule.runWithScope {
            // setup
            twitch.apply {
                val startTime = now + 2.days
                val segments = mapOf(
                    users[0] to listOf(streamSchedule(id = "stream-0", startTime = startTime, title = "title1")),
                    users[1] to listOf(streamSchedule(id = "stream-1", startTime = startTime, title = "title0")),
                )
                segments.forEach { (user, segment) ->
                    val updatable = channelSchedule(segment, user).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                    dao.replaceChannelScheduleEntitiesBatch(mapOf(user.id to updatable))
                }
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("stream-1", "stream-0")
            }
        }

        @Test
        fun sortByDatetimeAscendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = now,
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-1", startTime = now + 3.days),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-1-0", "stream-1")
            }
        }

        @Test
        fun sortByTitleAscendancy() = rule.runWithScope {
            // setup
            val startTime = now + 3.days
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = startTime,
                        title = "title0",
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-1", startTime = startTime, title = "title1"),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-1-0", "stream-1")
            }
        }
    }
}
