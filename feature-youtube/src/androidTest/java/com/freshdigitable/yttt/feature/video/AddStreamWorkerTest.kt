package com.freshdigitable.yttt.feature.video

import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.NetworkResponse
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.feature.timetable.video
import com.freshdigitable.yttt.logD
import com.freshdigitable.yttt.test.CallerVerifier
import com.freshdigitable.yttt.test.FakeDateTimeProviderModule
import com.freshdigitable.yttt.test.FakeYouTubeClient
import com.freshdigitable.yttt.test.FakeYouTubeClientModule
import com.freshdigitable.yttt.test.InMemoryDbModule
import com.freshdigitable.yttt.test.TestCoroutineScopeModule
import com.freshdigitable.yttt.test.TestCoroutineScopeRule
import com.freshdigitable.yttt.test.fromRemote
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
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
            videoList = caller.wrap(expected = 1) {
                listOf(video).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
            channelList = caller.wrap(expected = 1) {
                listOf(channelDetail).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
        )
        hiltRule.inject()
        initTestWorkManager()
        val data =
            AddStreamUseCase.Input.create("https://youtube.com/live/${video.id.value}".toUri())!!
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
    lateinit var localSource: YouTubeDataSource.Local

    @Inject
    lateinit var extendedSource: YouTubeDataSource.Extended

    @Test
    fun validUrlForFreeChat_stateIsSucceeded() = testScope.runTest {
        // setup
        val channelDetail = FakeYouTubeClient.channelDetail(1)
        val video = video(1, channelDetail)
        FakeYouTubeClientModule.client = FakeYouTubeClientImpl(
            videoList = caller.wrap(expected = 1) {
                listOf(video).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
            channelList = caller.wrap(expected = 1) {
                listOf(channelDetail).toUpdatable(CacheControl.fromRemote(Instant.EPOCH))
            },
        )
        hiltRule.inject()
        initTestWorkManager()
        val uri = "https://youtube.com/live/${video.id.value}".toUri()
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

class FakeYouTubeClientImpl(
    private val videoList: ((Set<YouTubeVideo.Id>) -> Updatable<List<YouTubeVideo>>)? = null,
    private val channelList: ((Set<YouTubeChannel.Id>) -> Updatable<List<YouTubeChannel>>)? = null,
) : FakeYouTubeClient() {
    override fun fetchVideoList(ids: Set<YouTubeVideo.Id>): NetworkResponse<List<YouTubeVideo>> {
        logD { "fetchVideoList: $ids" }
        return NetworkResponse.create(videoList!!.invoke(ids))
    }

    override fun fetchChannelList(ids: Set<YouTubeChannel.Id>): NetworkResponse<List<YouTubeChannel>> {
        logD { "fetchChannelList: $ids" }
        return NetworkResponse.create(channelList!!.invoke(ids))
    }
}

interface FakeRemoteSourceModule : FakeYouTubeClientModule
interface FakeDateTimeProviderModuleImpl : FakeDateTimeProviderModule
interface TestCoroutineScopeModuleImpl : TestCoroutineScopeModule
interface InMemoryDbModuleImpl : InMemoryDbModule
