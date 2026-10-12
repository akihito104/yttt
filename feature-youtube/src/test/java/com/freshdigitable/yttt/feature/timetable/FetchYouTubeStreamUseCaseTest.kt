package com.freshdigitable.yttt.feature.timetable

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(Enclosed::class)
internal class FetchYouTubeStreamUseCaseTest : FetchYouTubeStreamUseCaseTestImpl() {
    @HiltAndroidTest
    @Config(application = HiltTestApplication::class, sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    internal class Init : InitImpl()

    @HiltAndroidTest
    @Config(application = HiltTestApplication::class, sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    internal class AfterInit : AfterInitImpl()

    @HiltAndroidTest
    @Config(application = HiltTestApplication::class, sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    internal class SubscriptionIsNotModified : SubscriptionIsNotModifiedImpl()
}
