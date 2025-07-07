package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.local.TwitchDataSourceTestRule
import com.freshdigitable.yttt.data.source.local.userDetail
import com.freshdigitable.yttt.test.zero
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TwitchPagingSourceImplTest {
    @get:Rule
    internal val rule = TwitchDataSourceTestRule()

    private companion object {
        private val me = userDetail(id = "user_me")
        private val fetchedAt = Instant.EPOCH
        private val maxAge = Duration.ofMinutes(5)
        private val followings =
            TwitchFollowings.create(me.id, emptyList(), CacheControl.create(fetchedAt, maxAge))
    }

    @Test
    fun isUpdatable_returnsFalse() = rule.runWithLocalSource {
        // setup
        dataSource.setMe(me.toUpdatable(CacheControl.zero()))
        dataSource.replaceAllFollowings(followings)
        val sut = TwitchPagingSourceImpl(rule.database)
        // exercise
        val actual = sut.isUpdatable((fetchedAt + maxAge).minusMillis(1))
        // verify
        assertThat(actual).isFalse()
    }

    @Test
    fun isUpdatable_returnsTrue() = rule.runWithLocalSource {
        // setup
        dataSource.setMe(me.toUpdatable(CacheControl.zero()))
        dataSource.replaceAllFollowings(followings)
        val sut = TwitchPagingSourceImpl(rule.database)
        // exercise
        val actual = sut.isUpdatable(fetchedAt + maxAge)
        // verify
        assertThat(actual).isTrue()
    }
}
