package com.freshdigitable.yttt.data.source.local.db

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@Config(sdk = [26, 33])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LiveVideoOnAirItemDaoTest : LiveVideoOnAirItemDaoTestImpl() {
    class Init : InitImpl()
    class Sorting : SortingImpl()
}
