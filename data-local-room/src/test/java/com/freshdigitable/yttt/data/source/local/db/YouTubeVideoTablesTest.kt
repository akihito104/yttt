package com.freshdigitable.yttt.data.source.local.db

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

class YouTubeVideoTablesTest : YouTubeVideoTablesTestImpl() {
    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class Init : InitImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class ItemsAddedByDao : ItemsAddedByDaoImpl()
}
