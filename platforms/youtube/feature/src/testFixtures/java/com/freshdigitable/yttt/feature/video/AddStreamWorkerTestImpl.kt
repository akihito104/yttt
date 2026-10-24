package com.freshdigitable.yttt.feature.video

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.di.YouTubeModule
import com.freshdigitable.yttt.feature.timetable.videoJson
import com.freshdigitable.yttt.test.ChannelItemJson
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDatabaseModule
import com.freshdigitable.yttt.test.InMemoryDbRule
import com.freshdigitable.yttt.test.MockServerDispatcher.ExpectedResponse
import com.freshdigitable.yttt.test.MockServerRule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.youtubeChannel
import com.freshdigitable.yttt.test.youtubeVideo
import dagger.hilt.android.testing.HiltAndroidRule
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
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal abstract class AddStreamWorkerTestImpl {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val testScope = TestCoroutineScopeRule()

    @get:Rule(order = 2)
    val server = MockServerRule { YouTubeModule.rootUrl = it.toString() }

    @get:Rule(order = 3)
    val dbRule = InMemoryDbRule()

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
        FakeDateTimeProviderModule.instant = Instant.fromEpochMilliseconds(0)
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
        server.addResponses(
            ExpectedResponse.youtubeVideo(listOf(video)),
            ExpectedResponse.youtubeChannel(listOf(channelDetail)),
        )
        hiltRule.inject()
        initTestWorkManager()
        val data = AddStreamUseCase.Input.create("https://youtube.com/live/${video.id.value}".toUri())!!
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        server.addResponses(
            ExpectedResponse.youtubeVideo(listOf(video)),
            ExpectedResponse.youtubeChannel(listOf(channelDetail)),
        )
        hiltRule.inject()
        initTestWorkManager()
        val uri = "https://youtube.com/live/${video.id.value}".toUri()
        val data = AddStreamUseCase.Input.create(uri, true)!!
        val context = ApplicationProvider.getApplicationContext<Context>()
        // exercise
        val actual = AddStreamWorker.enqueue(context, data)
            .firstOrNull { it?.state?.isFinished == true }
        // verify
        actual?.state shouldBe WorkInfo.State.SUCCEEDED
        val actualVideo = extendedSource.fetchVideoList(setOf(video.id)).getOrThrow().first()
        actualVideo.item.isFreeChat!!.shouldBeTrue()
    }

    private fun initTestWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDatabaseModule
