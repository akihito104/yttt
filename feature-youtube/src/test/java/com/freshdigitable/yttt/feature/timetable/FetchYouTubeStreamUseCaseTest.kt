package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.MockkResponseRule
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.YouTubeVideo
import io.mockk.called
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class FetchYouTubeStreamUseCaseTest {
    private val responseRule = MockkResponseRule()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(MockKRule(this))
        .around(responseRule)

    @MockK
    lateinit var liveRepository: YouTubeRepository

    @MockK
    lateinit var accountRepository: YouTubeAccountRepository

    @MockK
    lateinit var settingRepository: SettingRepository

    @MockK
    lateinit var dateTimeProvider: DateTimeProvider

    private val c = newSingleThreadContext("io thread")
    private val coroutineScope = CoroutineScope(c)

    @Before
    fun setup() {
        Dispatchers.setMain(c)
    }

    fun tearDown() {
        Dispatchers.resetMain()
        c.close()
    }

    private val sut: FetchYouTubeStreamUseCase by lazy {
        FetchYouTubeStreamUseCase(
            liveRepository,
            YouTubeFacade(liveRepository),
            accountRepository,
            settingRepository,
            dateTimeProvider,
            coroutineScope,
        )
    }

    @Test
    fun testInvokeWithEmptyItems() = runTest {
        // setup
        responseRule.apply {
            addMocks(liveRepository, accountRepository, settingRepository, dateTimeProvider)
            liveRepository.apply {
                coRegister { videos } answers {
                    emptyFlow<List<YouTubeVideo>>()
                        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())
                }
                coRegister { fetchPagedSubscriptionSummary() } returns emptyFlow()
                coRegister { cleanUp() } just runs
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
        coVerify(exactly = 0) {
            liveRepository.fetchPlaylistItemSummaries(any(), any(), any()) wasNot called
        }
    }

    @Test
    fun testInvokeNopWhenNoAccount() = runBlocking {
        // setup
        responseRule.apply {
            addMocks(liveRepository, accountRepository, settingRepository, dateTimeProvider)
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
