package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeVideo.Companion.extend
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended.Companion.isUpcomingWithinPublicationDeadline
import com.freshdigitable.yttt.test.fromRemote
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

class YouTubeVideoExtendedTest : ShouldSpec(
    {
        context("ChangeFromUpcomingForIsFreeChat") {
            val old = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "video",
                scheduledStartDateTime = Instant.ofEpochSecond(1000),
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
            val oldEx = old.extended(false).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))

            should("init_freeChatIsFalse") {
                // setup
                val current = old.toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(null)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("notUpdated_returnsFalse") {
                // setup
                val current = old.copy()
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changedTitleToFreeChat_returnsTrue") {
                // setup
                val current = old.copy(title = "free chat")
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe true
            }

            should("changedTitleToFreeChatAndArchived_returnsFalse") {
                // setup
                val current = old.copy(
                    title = "free chat",
                    liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
                ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changedTitle_freeChatIsFalse") {
                // setup
                val current = old.copy(title = "changed title")
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("extendedObjectIsNotExtendedAnyMore") {
                // setup
                val current = old.copy(title = "changed title")
                    .extended(true, old.channel)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual shouldBeSameInstanceAs current // isSameAs -> shouldBeSameInstanceAs
                actual.item.isFreeChat shouldBe true
            }
        }

        context("ChangeFromUpcomingForIsThumbnailUpdatable") {
            val old = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "video",
                scheduledStartDateTime = Instant.ofEpochSecond(1000),
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
            val oldEx = old.extended(false).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))

            should("init_thumbnailIsNotUpdatable") {
                // setup
                val current = old.toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(null)
                // verify
                actual.item.isThumbnailUpdatable shouldBe false
            }

            should("notUpdated_returnsFalse") {
                // setup
                val current = old.copy()
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isThumbnailUpdatable shouldBe false
            }

            should("notUpdatedButFreeChat_returnsTrue") {
                // setup
                val current = old.copy().toUpdatable<YouTubeVideo>(
                    fetchedAt = Instant.EPOCH + YouTubeVideo.MAX_AGE_FREE_CHAT,
                    maxAge = Duration.ZERO,
                )
                val oldAsFreeChat =
                    old.toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                        .extend(old = null, isFreeChat = true)
                // exercise
                val actual = current.extend(oldAsFreeChat, isFreeChat = true)
                // verify
                actual.item.isThumbnailUpdatable shouldBe true
            }

            should("liveIsArchived_returnsFalse") {
                // setup
                val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isThumbnailUpdatable shouldBe false
            }

            should("titleIsChangedAndArchived_returnsFalse") {
                // setup
                val current = old.copy(
                    liveBroadcastContent = YouTubeVideo.BroadcastType.NONE,
                    title = "changed title",
                ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isThumbnailUpdatable shouldBe false
            }

            should("liveIsStarted_returnsTrue") {
                // setup
                val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isThumbnailUpdatable shouldBe true
            }

            should("titleIsChanged_returnsTrue") {
                // setup
                val current = old.copy(title = "changed title")
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isThumbnailUpdatable shouldBe true
            }

            should("objectIsNotCreatedByExtendCreator_returnsFalse") {
                // setup
                val current = old.copy(title = "changed title")
                    .extended(channel = old.channel, isFreeChat = true)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual shouldBeSameInstanceAs current // isSameAs -> shouldBeSameInstanceAs
                actual.item.isThumbnailUpdatable shouldBe false
            }
        }

        context("ChangeFromFreeChat") {
            val old = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "free chat",
                scheduledStartDateTime = Instant.ofEpochSecond(1000),
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            )
            val oldEx = old.extended(true).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))

            should("init_returnsTrue") {
                // setup
                val current = old.toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(null)
                // verify
                actual.item.isFreeChat shouldBe true
            }

            should("notUpdated_returnsTrue") {
                // setup
                val current = old.copy()
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe true
            }

            should("changeTitle_returnsFalse") {
                // setup
                val current = old.copy(title = "changed title")
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changeTitleToLiveStream_returnsFalse") {
                // setup
                val current = old.copy(
                    title = "recycle free chat",
                    liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
                ).toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changeToLiveStream_returnsFalse") {
                // setup
                val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changeToArchived_returnsFalse") {
                // setup
                val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual.item.isFreeChat shouldBe false
            }

            should("changeToExtended_returnsSameObject") {
                // setup
                val current = old.copy(liveBroadcastContent = YouTubeVideo.BroadcastType.NONE)
                    .extended(isFreeChat = false, channel = old.channel)
                    .toUpdatable<YouTubeVideo>(CacheControl.fromRemote(Instant.EPOCH))
                // exercise
                val actual = current.extend(oldEx)
                // verify
                actual shouldBeSameInstanceAs current
            }
        }

        context("IsUpcomingWithinPublicationDeadline") {
            val scheduledStartDateTime = Instant.parse("2025-01-23T02:00:00.000+09:00")
            val video = YouTubeVideoImpl(
                id = YouTubeVideo.Id("video"),
                title = "title",
                scheduledStartDateTime = scheduledStartDateTime,
                liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
            ).extended(false)

            should("returnsTrue") {
                // setup
                val current = scheduledStartDateTime + LiveVideo.UPCOMING_DEADLINE
                // exercise
                val actual = video.isUpcomingWithinPublicationDeadline(current)
                // verify
                actual shouldBe true
            }

            should("returnsFalse") {
                // setup
                val current = scheduledStartDateTime + LiveVideo.UPCOMING_DEADLINE.plusMillis(1)
                // exercise
                val actual = video.isUpcomingWithinPublicationDeadline(current)
                // verify
                actual shouldBe false
            }

            should("scheduledStartDateTimeIsNull_throwsIllegalStateException") {
                // setup
                val videoNoSchedule = YouTubeVideoImpl(
                    id = YouTubeVideo.Id("video"),
                    title = "title",
                    liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                ).extended(false)
                val current = scheduledStartDateTime + LiveVideo.UPCOMING_DEADLINE
                // exercise
                shouldThrow<IllegalStateException> {
                    videoNoSchedule.isUpcomingWithinPublicationDeadline(current)
                }
            }

            should("live_throwsIllegalStateException") {
                // setup
                val liveVideo = YouTubeVideoImpl(
                    id = YouTubeVideo.Id("video"),
                    title = "title",
                    scheduledStartDateTime = scheduledStartDateTime,
                    liveBroadcastContent = YouTubeVideo.BroadcastType.LIVE,
                ).extended(false)
                val current = scheduledStartDateTime + LiveVideo.UPCOMING_DEADLINE
                // exercise
                shouldThrow<IllegalStateException> {
                    liveVideo.isUpcomingWithinPublicationDeadline(current)
                }
            }

            should("freeChat_throwsIllegalStateException") {
                // setup
                val freeChatVideo = YouTubeVideoImpl(
                    id = YouTubeVideo.Id("video"),
                    title = "title",
                    scheduledStartDateTime = scheduledStartDateTime,
                    liveBroadcastContent = YouTubeVideo.BroadcastType.UPCOMING,
                ).extended(true)
                val current = scheduledStartDateTime + LiveVideo.UPCOMING_DEADLINE
                // exercise
                shouldThrow<IllegalStateException> {
                    freeChatVideo.isUpcomingWithinPublicationDeadline(current)
                }
            }
        }
    },
)

internal data class YouTubeVideoImpl(
    override val id: YouTubeVideo.Id,
    override val title: String,
    override val channel: YouTubeChannel = YouTubeChannelEntity(
        id = YouTubeChannel.Id("channel"),
        title = "channel",
        iconUrl = "",
    ),
    override val thumbnailUrl: String = "https://example.com/user/live/thumnail.png",
    override val scheduledStartDateTime: Instant? = null,
    override val scheduledEndDateTime: Instant? = null,
    override val actualStartDateTime: Instant? = null,
    override val actualEndDateTime: Instant? = null,
    override val description: String = "",
    override val viewerCount: BigInteger? = null,
    override val liveBroadcastContent: YouTubeVideo.BroadcastType,
) : YouTubeVideo

internal fun YouTubeVideoImpl.extended(
    isFreeChat: Boolean,
    channel: YouTubeChannel = this.channel,
): YouTubeVideoExtended = object : YouTubeVideoExtended, YouTubeVideo by this {
    override val channel: YouTubeChannel get() = channel
    override val isFreeChat: Boolean = isFreeChat
}
