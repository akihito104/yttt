package com.freshdigitable.yttt.data.source.local.db

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

class LiveVideoOnAirItemDaoTest : LiveVideoOnAirItemDaoTestImpl() {
    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class Init : InitImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class Sorting : SortingImpl()
}
