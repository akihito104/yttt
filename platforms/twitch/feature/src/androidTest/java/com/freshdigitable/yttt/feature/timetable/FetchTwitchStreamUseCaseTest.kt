package com.freshdigitable.yttt.feature.timetable

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
internal class FetchTwitchStreamUseCaseTest : FetchTwitchStreamUseCaseTestImpl() {
    @HiltAndroidTest
    class Init : InitImpl()

    @HiltAndroidTest
    class HasItems : HasItemsImpl()
}
