package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.TwitchFollowings.Companion.update
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

@RunWith(Enclosed::class)
class TwitchFollowingsTest {
    private companion object {
        private val me = TwitchUser.Id("me")
    }

    class Init {
        @Test
        fun create_maxAgeIsDefaultValue() {
            // exercise
            val sut = TwitchFollowings.create(me, emptyList(), null)
            // verify
            assertThat(sut.cacheControl.fetchedAt).isNull()
            assertThat(sut.cacheControl.maxAge).isNull()
        }
    }

    @RunWith(Parameterized::class)
    internal class UpdateHasNoRemovedItem(private val params: TestParam) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{0}")
            internal fun params(): List<TestParam> = listOf(
                TestParam(name = "both empty", old = emptyList(), new = emptyList()),
                TestParam(name = "new following is added", old = emptyList(), new = listOf(0)),
                TestParam(
                    name = "unfollowed all",
                    old = listOf(0, 1, 2),
                    new = listOf(0, 1, 2),
                ),
            )
        }

        @Test
        fun test() {
            // setup
            val sut = followings(me, params.old.broadcasters(), 99)
                .update(followings(me, params.new.broadcasters(), 100))
            // exercise
            val actual = sut.item.removed
            // verify
            assertThat(actual).isEmpty()
        }

        internal data class TestParam(
            val name: String,
            val old: Collection<Int>,
            val new: Collection<Int>,
        )
    }

    @RunWith(Parameterized::class)
    internal class UpdateHasRemovedItems(private val params: TestParam) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{0}")
            internal fun params(): List<TestParam> = listOf(
                TestParam(
                    name = "unfollowed",
                    old = listOf(0),
                    new = emptyList(),
                    expectedIndexOfOld = listOf(0),
                ),
                TestParam(
                    name = "unfollowed id=1, and followed id=3",
                    old = listOf(0, 1, 2),
                    new = listOf(0, 2, 3),
                    expectedIndexOfOld = listOf(1),
                ),
                TestParam(
                    name = "unfollowed id=1,3",
                    old = listOf(0, 1, 2, 3),
                    new = listOf(0, 2),
                    expectedIndexOfOld = listOf(1, 3),
                ),
                TestParam(
                    name = "unfollowed id=0,1,2, and followed id=3,4,5",
                    old = listOf(0, 1, 2),
                    new = listOf(3, 4, 5),
                    expectedIndexOfOld = listOf(0, 1, 2),
                ),
            )
        }

        @Test
        fun test() {
            // setup
            val sut = followings(me, params.oldBroadcasters, 99)
                .update(followings(me, params.new.broadcasters(), 100))
            // exercise
            val actual = sut.item.removed
            // verify
            assertThat(actual).containsExactlyInAnyOrderElementsOf(params.expected)
        }

        internal data class TestParam(
            val name: String,
            val old: Collection<Int>,
            val new: Collection<Int>,
            val expectedIndexOfOld: Collection<Int>,
        ) {
            val oldBroadcasters: List<TwitchBroadcaster> = old.broadcasters()
            val expected: List<TwitchUser.Id> = expectedIndexOfOld.map { oldBroadcasters[it].id }
            override fun toString(): String = name
        }
    }

    class UpdateThrowsException {
        @Test
        fun updatableAtOfNewIsNotNewer_throwsIllegalArgumentException() {
            // exercise
            val actual = catchThrowable {
                followings(me, emptyList(), 100)
                    .update(followings(me, emptyList(), 99))
            }
            // verify
            assertThat(actual).isExactlyInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun followerIsNotSame_throwsIllegalArgumentException() {
            // setup
            val others = TwitchUser.Id("me2")
            // exercise
            val actual = catchThrowable {
                followings(me, emptyList(), 99)
                    .update(followings(others, emptyList(), 100))
            }
            // verify
            assertThat(actual).isExactlyInstanceOf(IllegalArgumentException::class.java)
        }
    }
}

private fun followings(
    me: TwitchUser.Id,
    followings: List<TwitchBroadcaster>,
    fetchedAtMillis: Long,
): Updatable<TwitchFollowings> = TwitchFollowings.create(
    me,
    followings,
    CacheControl.create(Instant.ofEpochMilli(fetchedAtMillis), null),
)

private fun Collection<Int>.broadcasters(): List<TwitchBroadcaster> =
    map { broadcaster("broadcaster$it") }

private fun broadcaster(
    id: String,
    followedAt: Instant = Instant.EPOCH,
): TwitchBroadcaster = object : TwitchBroadcaster {
    override val id: TwitchUser.Id get() = TwitchUser.Id(id)
    override val loginName: String get() = id
    override val displayName: String get() = id
    override val followedAt: Instant get() = followedAt
}
