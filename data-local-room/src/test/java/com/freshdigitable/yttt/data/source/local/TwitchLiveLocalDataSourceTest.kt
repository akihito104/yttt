package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

class TwitchLiveLocalDataSourceTest : TwitchLiveLocalDataSourceTestImpl() {
    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class SingleAccount : SingleAccountImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class MultiAccount : MultiAccountImpl()
}
