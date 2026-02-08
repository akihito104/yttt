package com.freshdigitable.yttt.feature.video

import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.feature.timetable.videoJson
import com.freshdigitable.yttt.test.CallerVerifier
import com.freshdigitable.yttt.test.ChannelItemJson
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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

    @get:Rule(order = 3)
    val server = MockServerRule()

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
    fun invalidUrl_inputDataIsNull() = testScope.runTest {
        // exercise
        val data = AddStreamUseCase.Input.create("https://example.com/".toUri())
        // verify
        data.shouldBeNull()
    }

    @Test
    fun validUrl_stateIsSucceeded() = testScope.runTest {
        // setup
        val channelDetail = ChannelItemJson.createSnippet(YouTubeChannel.Id("channel_1"))
        val video = videoJson(1, channelDetail)
        server.setClient(
            videoList = caller.wrap(expected = 1) { listOf(video) },
            channelList = caller.wrap(expected = 1) { (ids, _) ->
                check(setOf(channelDetail.id) == ids)
                listOf(channelDetail)
            },
        )
        hiltRule.inject()
        initTestWorkManager()
        val data = AddStreamUseCase.Input.create("https://youtube.com/live/${video.id}".toUri())!!
        val context = InstrumentationRegistry.getInstrumentation().context
        // exercise
        val actual = AddStreamWorker.enqueue(context, data)
            .firstOrNull { it?.state?.isFinished == true }
        // verify
        actual?.state shouldBe WorkInfo.State.SUCCEEDED
        val actualVideo = extendedSource.fetchVideoList(setOf(video.id)).getOrThrow().first()
        actualVideo.item.isFreeChat!!.shouldBeFalse()
    }

    @Inject
    lateinit var extendedSource: YouTubeDataSource.Extended

    @Test
    fun validUrlForFreeChat_stateIsSucceeded() = testScope.runTest {
        // setup
        val channelDetail = ChannelItemJson.createSnippet(YouTubeChannel.Id("channel_1"))
        val video = videoJson(1, channelDetail)
        server.setClient(
            videoList = caller.wrap(expected = 1) { listOf(video) },
            channelList = caller.wrap(expected = 1) { (ids, _) ->
                check(setOf(channelDetail.id) == ids)
                listOf(channelDetail)
            },
        )
        hiltRule.inject()
        initTestWorkManager()
        val uri = "https://youtube.com/live/${video.id}".toUri()
        val data = AddStreamUseCase.Input.create(uri, true)!!
        val context = InstrumentationRegistry.getInstrumentation().context
        // exercise
        val actual = AddStreamWorker.enqueue(context, data)
            .firstOrNull { it?.state?.isFinished == true }
        // verify
        actual?.state shouldBe WorkInfo.State.SUCCEEDED
        val actualVideo = extendedSource.fetchVideoList(setOf(video.id)).getOrThrow().first()
        actualVideo.item.isFreeChat!!.shouldBeTrue()
    }

    private fun initTestWorkManager() {
        val context = InstrumentationRegistry.getInstrumentation().context
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            config,
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
