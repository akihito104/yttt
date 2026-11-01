package com.freshdigitable.yttt.feature.timetable

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
internal class FetchYouTubeStreamUseCaseTest : FetchYouTubeStreamUseCaseTestImpl() {
    @HiltAndroidTest
    internal class Init : InitImpl()

    @HiltAndroidTest
    internal class AfterInit : AfterInitImpl()

    @HiltAndroidTest
    internal class SubscriptionIsNotModified : SubscriptionIsNotModifiedImpl()
}
