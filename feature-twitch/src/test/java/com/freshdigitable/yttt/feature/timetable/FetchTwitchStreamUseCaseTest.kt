package com.freshdigitable.yttt.feature.timetable

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Enclosed::class)
internal class FetchTwitchStreamUseCaseTest : FetchTwitchStreamUseCaseTestImpl() {
    @HiltAndroidTest
    @Config(application = HiltTestApplication::class)
    class Init : InitImpl()

    @HiltAndroidTest
    @Config(application = HiltTestApplication::class)
    class HasItems : HasItemsImpl()
}
