package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.TwitchFollowings
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.source.local.fixture.DatabaseExtension
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceExtension
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceTestScope
import com.freshdigitable.yttt.data.source.local.userDetail
import com.freshdigitable.yttt.test.zero
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.Instant

@ExtendWith(DatabaseExtension::class, TwitchDataSourceExtension::class)
class TwitchPagingSourceImplTest {
    private companion object {
        private val me = userDetail(id = "user_me")
        private val fetchedAt = Instant.EPOCH
        private val maxAge = Duration.ofMinutes(5)
        private val followings =
            TwitchFollowings.create(me.id, emptyList(), CacheControl.create(fetchedAt, maxAge))
    }

    internal lateinit var sut: TwitchPagingSourceImpl

    @BeforeEach
    internal fun TwitchDataSourceTestScope.setup() = scopedTest {
        dataSource.setMe(me.toUpdatable(CacheControl.zero()))
        dataSource.replaceAllFollowings(followings)
        sut = TwitchPagingSourceImpl(database)
    }

    @Test
    internal fun isUpdatable_returnsFalse() = runTest {
        // setup
        val current = (fetchedAt + maxAge).minusMillis(1)
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
