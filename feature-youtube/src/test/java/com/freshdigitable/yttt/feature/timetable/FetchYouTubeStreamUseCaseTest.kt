package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class FetchYouTubeStreamUseCaseTest {
    @Test
    fun testInvokeWithEmptyItems() = runBlocking {
        // setup
        val liveRepository = mockk<YouTubeRepository>().apply {
            coEvery { findAllUnfinishedVideos() } returns emptyList()
            coEvery { removeVideo(any()) } just runs
            coEvery { fetchAllSubscribeSummary() } returns emptyList()
            coEvery { cleanUp() } just runs
        }
        val facade = mockk<YouTubeFacade>().apply {
            coEvery { fetchVideoList(any()) } returns emptyList()
            coEvery { updateAsFreeChat() } just runs
        }
        val accountRepository = mockk<AccountRepository>().apply {
            every { hasAccount() } returns true
        }
        val settingRepository = mockk<SettingRepository>().apply {
            every { lastUpdateDatetime = any() } just runs
        }
        val dateTimeProvider = mockk<DateTimeProvider>().apply {
            every { now() } returns Instant.parse("2023-12-12T18:00:00.000Z")
        }
        val sut = FetchYouTubeStreamUseCase(
            liveRepository,
            facade,
            accountRepository,
            settingRepository,
            dateTimeProvider,
        )

        // exercise
        sut.invoke()

        // verify
        verify {
            settingRepository.lastUpdateDatetime = eq(Instant.parse("2023-12-12T18:00:00.000Z"))
        }
        liveRepository.run {
            coVerify { findAllUnfinishedVideos() }
            coVerify { removeVideo(any()) }
            coVerify { fetchAllSubscribeSummary() }
            coVerify { cleanUp() }
        }
        facade.run {
            coVerify { fetchVideoList(any()) }
            coVerify { updateAsFreeChat() }
        }
        accountRepository.run {
            verify { hasAccount() }
        }
        settingRepository.run {
            verify { lastUpdateDatetime = any() }
        }
        dateTimeProvider.run {
            verify { now() }
        }
        confirmVerified(
            liveRepository,
            facade,
            accountRepository,
            settingRepository,
            dateTimeProvider,
        )
    }
}
