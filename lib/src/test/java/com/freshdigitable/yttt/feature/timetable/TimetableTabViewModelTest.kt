package com.freshdigitable.yttt.feature.timetable

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.paging.PagingData
import com.freshdigitable.yttt.compose.SnackbarMessageBus
import com.freshdigitable.yttt.data.SettingRepository
import com.freshdigitable.yttt.data.model.DateTimeProvider
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class TimetableTabViewModelTest : ShouldSpec(
    {
        val settingRepository = mockk<SettingRepository>(relaxed = true)
        val dateTimeProvider = mockk<DateTimeProvider>()
        val timetablePageDelegate = mockk<TimetablePageDelegate>()
        val contextMenuDelegate = mockk<TimetableContextMenuDelegate>(relaxed = true)
        val sender = mockk<SnackbarMessageBus.Sender>(relaxed = true)

        val testDispatcher = StandardTestDispatcher()

        beforeSpec {
            Dispatchers.setMain(testDispatcher)
            ArchTaskExecutor.getInstance().setDelegate(
                object : TaskExecutor() {
                    override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
                    override fun postToMainThread(runnable: Runnable) = runnable.run()
                    override fun isMainThread(): Boolean = true
                },
            )
        }

        afterSpec {
            Dispatchers.resetMain()
            ArchTaskExecutor.getInstance().setDelegate(null)
        }

        should("loadList completes other tasks even when one task fails with exception") {
            // setup
            val task1 = mockk<FetchStreamUseCase>()
            coEvery { task1.invoke() } coAnswers {
                delay(100.milliseconds)
                throw RuntimeException("task1 failed")
            }

            val task2 = mockk<FetchStreamUseCase>()
            var task2Completed = false
            coEvery { task2.invoke() } coAnswers {
                delay(500.milliseconds)
                task2Completed = true
                Result.success(Unit)
            }

            every { dateTimeProvider.now() } returns Instant.EPOCH
            every { timetablePageDelegate.getTimetableItemPager(any()) } returns { flowOf(PagingData.empty()) }
            val sut = TimetableTabViewModel(
                settingRepository,
                setOf(task1, task2),
                contextMenuDelegate,
                dateTimeProvider,
                timetablePageDelegate,
                sender,
            )

            // exercise
            sut.loadList()
            testDispatcher.scheduler.advanceUntilIdle()

            // verify
            task2Completed shouldBe true
        }
    },
)
