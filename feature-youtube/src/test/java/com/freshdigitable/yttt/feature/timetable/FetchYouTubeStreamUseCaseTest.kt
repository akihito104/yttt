package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.MockkResponseRule
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeVideoExtended
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FetchYouTubeStreamUseCaseTest {
    @get:Rule
    val responseRule = MockkResponseRule(this)

    @MockK
    lateinit var liveRepository: YouTubeRepository

    @MockK
    lateinit var accountRepository: YouTubeAccountRepository

    @MockK
    lateinit var dateTimeProvider: DateTimeProvider

    private val sut: FetchYouTubeStreamUseCase by lazy {
        FetchYouTubeStreamUseCase(liveRepository, accountRepository, dateTimeProvider)
    }

    @Test
    fun testInvokeWithEmptyItems() = runTest {
        // setup
        responseRule.apply {
            addMocks(liveRepository, accountRepository, dateTimeProvider)
            liveRepository.apply {
                coRegister { videos } answers {
                    emptyFlow<List<YouTubeVideoExtended>>()
                        .stateIn(this@runTest, SharingStarted.Eagerly, emptyList())
                }
                coRegister { fetchPagedSubscription() } returns emptyFlow()
                coRegister { cleanUp() } just runs
            }
            accountRepository.apply {
                register { hasAccount() } returns true
            }
            dateTimeProvider.apply {
                register { now() } returns Instant.parse("2023-12-12T18:00:00.000Z")
            }
        }

        // exercise
        sut.invoke()

        // verify
        coVerify(exactly = 0) {
            liveRepository.fetchPlaylistWithItems(any(), any(), any())
        }
    }

    @Test
    fun testInvokeNopWhenNoAccount() = runTest {
        // setup
        responseRule.apply {
            addMocks(liveRepository, accountRepository, dateTimeProvider)
            register { accountRepository.hasAccount() } returns false
        }

        // exercise
        sut.invoke()

        // verify
        coVerify(exactly = 0) {
            liveRepository.fetchPlaylistWithItems(any(), any(), any())
        }
    }
}
