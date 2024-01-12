package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.MockkResponseRule
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Instant

class FetchYouTubeStreamUseCaseTest {
    private val responseRule = MockkResponseRule()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(MockKRule(this))
        .around(responseRule)

    @MockK
    lateinit var liveRepository: YouTubeRepository

    @MockK
    lateinit var facade: YouTubeFacade

    @MockK
    lateinit var accountRepository: YouTubeAccountRepository

    @MockK
    lateinit var settingRepository: SettingRepository

    @MockK
    lateinit var dateTimeProvider: DateTimeProvider

    private val sut: FetchYouTubeStreamUseCase by lazy {
        FetchYouTubeStreamUseCase(
            liveRepository,
            facade,
            accountRepository,
            settingRepository,
            dateTimeProvider,
        )
    }

    @Test
    fun testInvokeWithEmptyItems() = runBlocking {
        // setup
        responseRule.apply {
            addMocks(liveRepository, facade, accountRepository, settingRepository, dateTimeProvider)
            liveRepository.apply {
                coRegister { findAllUnfinishedVideos() } returns emptyList()
                coRegister { removeVideo(any()) } just runs
                coRegister { fetchAllSubscribeSummary() } returns emptyList()
                coRegister { cleanUp() } just runs
            }
            facade.apply {
                coRegister { fetchVideoList(any()) } returns emptyList()
                coRegister { updateAsFreeChat() } just runs
            }
            accountRepository.apply {
                register { hasAccount() } returns true
            }
            settingRepository.apply {
                register { lastUpdateDatetime = any() } just runs
            }
            dateTimeProvider.apply {
                register { now() } returns Instant.parse("2023-12-12T18:00:00.000Z")
            }
        }

        // exercise
        sut.invoke()

        // verify
        verify {
            settingRepository.lastUpdateDatetime = eq(Instant.parse("2023-12-12T18:00:00.000Z"))
        }
    }

    @Test
    fun testInvokeNopWhenNoAccount() = runBlocking {
        // setup
        responseRule.apply {
            addMocks(liveRepository, facade, accountRepository, settingRepository, dateTimeProvider)
            register { accountRepository.hasAccount() } returns false
        }

        // exercise
        sut.invoke()

        // verify
        verify(exactly = 0) {
            settingRepository.lastUpdateDatetime = any()
        }
    }
}
