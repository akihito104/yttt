package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TwitchLiveLocalDataSourceTest : TwitchLiveLocalDataSourceTestImpl() {
    class SingleAccount : SingleAccountImpl()
    class MultiAccount : MultiAccountImpl()
}
