package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.source.local.TwitchDataSourceTestRule
import com.freshdigitable.yttt.data.source.local.followings
import com.freshdigitable.yttt.data.source.local.userDetail
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class TwitchPagingSourceImplTest {
    @get:Rule
    internal val rule = TwitchDataSourceTestRule()

    @Test
    fun isUpdatable_returnsFalse() = rule.runWithLocalSource {
        // setup
        val me = userDetail(id = "user_me")
        dataSource.setMe(me)
        val followings = followings(me.id, emptyList(), Instant.EPOCH)
        dataSource.replaceAllFollowings(followings)
        val sut = TwitchPagingSourceImpl(rule.database)
        // exercise
        val actual = sut.isUpdatable(followings.updatableAt.minusMillis(1))
        // verify
        assertThat(actual).isFalse()
    }

    @Test
    fun isUpdatable_returnsTrue() = rule.runWithLocalSource {
        // setup
        val me = userDetail(id = "user_me")
        dataSource.setMe(me)
        val followings = followings(me.id, emptyList(), Instant.EPOCH)
        dataSource.replaceAllFollowings(followings)
        val sut = TwitchPagingSourceImpl(rule.database)
        // exercise
        val actual = sut.isUpdatable(followings.updatableAt)
        // verify
        assertThat(actual).isTrue()
    }
}
