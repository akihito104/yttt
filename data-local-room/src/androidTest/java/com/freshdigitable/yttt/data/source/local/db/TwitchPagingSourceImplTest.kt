package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceTestRule
import com.freshdigitable.yttt.data.source.local.userDetail
import com.freshdigitable.yttt.test.zero
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TwitchPagingSourceImplTest {
    private companion object {
        private val me = userDetail(id = "user_me")
        private val fetchedAt = Instant.fromEpochMilliseconds(0)
        private val maxAge = 5.minutes
        private val followings =
            TwitchFollowings.create(me.id, emptyList(), CacheControl.create(fetchedAt, maxAge))
    }

    @get:Rule
    internal val rule = TwitchDataSourceTestRule()
    internal lateinit var sut: TwitchPagingSourceImpl

    @Before
    fun setup() = rule.runWithScope {
        localSource.setMe(me.toUpdatable(CacheControl.zero()))
        localSource.replaceAllFollowings(followings)
        sut = TwitchPagingSourceImpl(rule.database)
    }

    @Test
    internal fun isUpdatable_returnsFalse() = runTest {
        // setup
        val current = (fetchedAt + maxAge) - 1.milliseconds
        // exercise
        val actual = sut.isUpdatable(current)
        // verify
        actual shouldBe false
    }

    @Test
    internal fun isUpdatable_returnsTrue() = runTest {
        // setup
        val current = fetchedAt + maxAge
        // exercise
        val actual = sut.isUpdatable(current)
        // verify
        actual shouldBe true
    }
}
