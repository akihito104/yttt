package com.freshdigitable.yttt.feature

import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelDetail
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.feature.timetable.video
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.CallerVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@HiltAndroidTest
class AddStreamWorkerTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule()

    @get:Rule(order = 2)
    val caller = CallerVerifier()

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var coroutineDispatcher: CoroutineDispatcher

    private val config: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerCoroutineContext(coroutineDispatcher)
            .setExecutor(SynchronousExecutor())
            .build()
    }

    @Before
    fun setup() {
        FakeDateTimeProviderModule.instant = Instant.EPOCH
    }

    @Test
    fun invalidUrl_stateIsFailed() = testScope.runTest {
        // setup
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().context
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        // exercise
        val actual = AddStreamWorker.enqueue(context, "https://example.com/")
            .firstOrNull { it?.state?.isFinished == true }
        // verify
        assertThat(actual?.state).isEqualTo(WorkInfo.State.FAILED)
    }

    @Test
    fun validUrl_stateIsSucceeded() = testScope.runTest {
        // setup
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        FakeYouTubeClientModule.client = object : FakeYouTubeClient() {
            override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
                logD { "fetchVideoList: $ids" }
                return NetworkResponse.create(listOf(video))
            }

            override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannelDetail>> {
                logD { "fetchChannelList: $ids" }
                return NetworkResponse.create(listOf(channelDetail))
            }
        }
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().context
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, config, WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        // exercise
        val actual = AddStreamWorker.enqueue(context, "https://youtube.com/live/${video.id.value}")
            .firstOrNull { it?.state?.isFinished == true }
        // verify
        assertThat(actual?.state).isEqualTo(WorkInfo.State.SUCCEEDED)
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
