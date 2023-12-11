package com.freshdigitable.yttt.feature.timetable

import com.freshdigitable.yttt.data.AccountRepository
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.YouTubeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FetchYouTubeStreamUseCaseTest {
    @Test
    fun testInvokeWithEmptyItems() = runBlocking {
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
        val sut =
            FetchYouTubeStreamUseCase(liveRepository, facade, accountRepository, settingRepository)
        sut.invoke()
    }
}
