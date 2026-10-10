package com.freshdigitable.yttt.data

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [26, 33])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
internal class YouTubeRepositoryTest : YouTubeRepositoryTestImpl()
