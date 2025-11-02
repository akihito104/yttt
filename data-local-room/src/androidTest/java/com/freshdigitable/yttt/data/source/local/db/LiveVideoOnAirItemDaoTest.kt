package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchStreams
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchUserDetail
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.equals.shouldBeEqual
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(Enclosed::class)
class LiveVideoOnAirItemDaoTest {
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
        private val cacheControl = CacheControl.fromRemote(Instant.EPOCH)
    }

    class Init {
        @get:Rule
        internal val rule = LiveDataSourceTestRule()

        @Test
        fun init() = rule.runWithScope {
            dao.getAllOnAirPagingSource().testWithRefresh {
                data.shouldBeEmpty()
            }
        }

        @Test
        fun anyUpcomingItemsAreNotIncluded() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.upcomingStream(channel = channels[0]),
                    YouTubeVideoEntity.archivedStream(channel = channels[0]),
                    YouTubeVideoEntity.freeChat(channel = channels[0]),
                    YouTubeVideoEntity.unscheduledUpcoming(channel = channels[0]),
                    YouTubeVideoEntity.uploadedVideo(channel = channels[0]),
                )
                dao.addChannelEntities(channels)
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val streamSchedule = streamSchedule(id = "stream_id")
                val schedule = channelSchedule(listOf(streamSchedule), users[0])
                val updatable = schedule.toUpdatable<TwitchChannelSchedule?>(cacheControl)
                localSource.setMe(me.toUpdatable(cacheControl))
                localSource.replaceAllFollowings(followings(me.id, broadcasters))
                localSource.addUsers(users.map { it.toUpdatable(cacheControl) })
                dao.replaceChannelScheduleEntitiesBatch(mapOf(users[0].id to updatable))
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.shouldBeEmpty()
            }
        }

        @Test
        fun imageUrlIsReplacedWithSize() = rule.runWithScope {
            // setup
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(
                            id = "stream_id-0-0",
                            userDetail = users[0],
                            startedAt = Instant.ofEpochMilli(1000),
                            thumbnailUrlBase = "https://example.com/category-1/img={width}x{height}",
                        ),
                    ),
                    cacheControl = cacheControl,
                )
                localSource.setMe(me.toUpdatable(cacheControl))
                localSource.replaceAllFollowings(followings(me.id, broadcasters))
                localSource.addUsers(users.map { it.toUpdatable(cacheControl) })
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.first().thumbnailUrl shouldBeEqual "https://example.com/category-1/img=640x360"
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
                dao.setMeEntities(me.toUpdatable(cacheControl))
                dao.replaceAllBroadcasterEntities(followings(me.id, broadcasters))
                dao.addUserDetailEntities(users.map { it.toUpdatable(cacheControl) })
            }
        }

        @Test
        fun youtube_sortByDatetimeDescendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = Instant.ofEpochMilli(2000),
                    ),
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-1",
                        channel = channels[1],
                        scheduledStartDateTime = Instant.ofEpochMilli(1000),
                    ),
                )
                dao.addVideoEntities(videos)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-0-0", "video-0-1")
            }
        }

        @Test
        fun youtube_sortByTitleAscendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = Instant.ofEpochMilli(1000),
                        title = "title1",
                    ),
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-1",
                        channel = channels[1],
                        scheduledStartDateTime = Instant.ofEpochMilli(1000),
                        title = "title0",
                    ),
                )
                dao.addVideoEntities(videos)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-0-1", "video-0-0")
            }
        }

        @Test
        fun twitch_sortByDatetimeDescendancy() = rule.runWithScope {
            // setup
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(id = "stream_id-0-0", userDetail = users[0], startedAt = Instant.ofEpochMilli(1000)),
                        stream(id = "stream_id-1-0", userDetail = users[1], startedAt = Instant.ofEpochMilli(2000)),
                    ),
                    cacheControl = cacheControl,
                )
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("stream_id-1-0", "stream_id-0-0")
            }
        }

        @Test
        fun twitch_sortByTitleAscendancy() = rule.runWithScope {
            // setup
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(
                            id = "stream_id-0-0",
                            userDetail = users[0],
                            startedAt = Instant.ofEpochMilli(1000),
                            title = "title1",
                        ),
                        stream(
                            id = "stream_id-1-0",
                            userDetail = users[1],
                            startedAt = Instant.ofEpochMilli(1000),
                            title = "title0",
                        ),
                    ),
                    cacheControl = cacheControl,
                )
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("stream_id-1-0", "stream_id-0-0")
            }
        }

        @Test
        fun sortByDatetimeDescendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = Instant.ofEpochMilli(2000),
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(
                            id = "stream_id-0-0",
                            userDetail = users[0],
                            startedAt = Instant.ofEpochMilli(1000),
                        ),
                    ),
                    cacheControl = cacheControl,
                )
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("video-0-0", "stream_id-0-0")
            }
        }

        @Test
        fun sortByTitleAscendancy() = rule.runWithScope {
            // setup
            youtube.apply {
                val videos = listOf(
                    YouTubeVideoEntity.liveStreaming(
                        id = "video-0-0",
                        channel = channels[0],
                        scheduledStartDateTime = Instant.ofEpochMilli(1000),
                        title = "title1",
                    ),
                )
                dao.addVideoEntities(videos)
            }
            twitch.apply {
                val stream = TwitchStreams.create(
                    followerId = me.id,
                    streams = listOf(
                        stream(
                            id = "stream_id-0-0",
                            userDetail = users[0],
                            startedAt = Instant.ofEpochMilli(1000),
                            title = "title0",
                        ),
                    ),
                    cacheControl = cacheControl,
                )
                dao.replaceAllStreams(stream)
            }
            // exercise
            dao.getAllOnAirPagingSource().testWithRefresh {
                // verify
                data.map { it.id.value }.shouldContainInOrder("stream_id-0-0", "video-0-0")
            }
        }
    }
}

internal fun category(
    id: String,
    artUrlBase: String = "<url is here>",
): TwitchCategory = object : TwitchCategory {
    override val id: TwitchCategory.Id get() = TwitchCategory.Id(id)
    override val name: String get() = "name"
    override val artUrlBase: String get() = artUrlBase
    override val igdbId: String get() = id
}

internal fun stream(
    id: String,
    category: TwitchCategory = category("category_id"),
    thumbnailUrlBase: String = "<url is here>",
    userDetail: TwitchUserDetail,
    startedAt: Instant = Instant.EPOCH,
    title: String = "",
): TwitchStream = object : TwitchStream {
    override val id: TwitchStream.Id get() = TwitchStream.Id(id)
    override val gameId: TwitchCategory.Id get() = category.id
    override val gameName: String get() = category.name
    override val type: String get() = "type"
    override val startedAt: Instant get() = startedAt
    override val tags: List<String> get() = emptyList()
    override val isMature: Boolean get() = false
    override val user: TwitchUser get() = userDetail
    override val title: String get() = title
    override val thumbnailUrlBase: String get() = thumbnailUrlBase
    override val viewCount: Int get() = 100
    override val language: String get() = "ja"
}
