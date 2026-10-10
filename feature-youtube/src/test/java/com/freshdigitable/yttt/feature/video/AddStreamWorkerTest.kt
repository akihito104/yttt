package com.freshdigitable.yttt.feature.video

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
internal class AddStreamWorkerTest : AddStreamWorkerTestImpl()
