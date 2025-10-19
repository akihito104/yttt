package com.freshdigitable.yttt.data.source.local.db

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
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class LiveTimelineUpcomingItemDaoTest {
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
        private val cacheControl = CacheControl.Companion.fromRemote(now)
    }

    class Init {
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
                    YouTubeVideoEntity.Companion.liveStreaming(channel = channels[0]),
                    YouTubeVideoEntity.Companion.archivedStream(channel = channels[0]),
                    YouTubeVideoEntity.Companion.uploadedVideo(channel = channels[0]),
                    YouTubeVideoEntity.Companion.freeChat(channel = channels[0]),
                    YouTubeVideoEntity.Companion.unscheduledUpcoming(channel = channels[0]), // should have startScheduleDateTime
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val stream = TwitchStreams.Companion.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(id = "stream_id-0-0", userDetail = users[0], startedAt = now - Duration.ofHours(2)),
                    ),
                    cacheControl = CacheControl.Companion.fromRemote(now),
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
                    YouTubeVideoEntity.Companion.upcomingStream(
                        channel = channels[0],
                        scheduledStartDateTime = now - (Duration.ofHours(6) + Duration.ofSeconds(1)),
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-0", startTime = now - (Duration.ofHours(6) + Duration.ofSeconds(1))),
                    streamSchedule(id = "stream-1", startTime = now + (Duration.ofDays(7) + Duration.ofSeconds(1))),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
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
                        startTime = now + Duration.ofDays(2),
                        category = category(id = "category-1", artUrlBase = "https://example.com/{width}x{height}"),
                    ),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
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
                        startTime = now + Duration.ofDays(2),
                        category = null,
                    ),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
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

    class Sorting {
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
                    YouTubeVideoEntity.Companion.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = now,
                    ),
                    YouTubeVideoEntity.Companion.upcomingStream(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = now - Duration.ofHours(6),
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
                val scheduledStartDateTime = now + Duration.ofHours(2)
                val videos = listOf(
                    YouTubeVideoEntity.Companion.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = scheduledStartDateTime,
                        title = "title0",
                    ),
                    YouTubeVideoEntity.Companion.upcomingStream(
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
                    streamSchedule(id = "stream-0", startTime = now + Duration.ofDays(2)),
                    streamSchedule(id = "stream-1", startTime = now + Duration.ofDays(3)),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
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
                val startTime = now + Duration.ofDays(2)
                val segments = mapOf(
                    users[0] to listOf(streamSchedule(id = "stream-0", startTime = startTime, title = "title1")),
                    users[1] to listOf(streamSchedule(id = "stream-1", startTime = startTime, title = "title0")),
                )
                segments.forEach { (user, segment) ->
                    val updatable = channelSchedule(segment, user).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                    dao.replaceChannelScheduleEntities(user.id, updatable)
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
                    YouTubeVideoEntity.Companion.upcomingStream(
                        id = "video-1-0",
                        channel = channels[1],
                        scheduledStartDateTime = now,
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val segments = listOf(
                    streamSchedule(id = "stream-1", startTime = now + Duration.ofDays(3)),
                )
                val updatable = channelSchedule(segments, users[0]).toUpdatable<TwitchChannelSchedule?>(cacheControl)
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
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
            val startTime = now + Duration.ofDays(3)
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.Companion.upcomingStream(
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
                dao.replaceChannelScheduleEntities(users[0].id, updatable)
            }
            // exercise
            dao.getAllUpcomingPagingSource(now).testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-1-0", "stream-1")
            }
        }
    }
}
