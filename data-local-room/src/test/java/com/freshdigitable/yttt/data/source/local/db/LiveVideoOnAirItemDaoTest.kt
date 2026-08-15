package com.freshdigitable.yttt.data.source.local.db

import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LiveVideoOnAirItemDaoTest : LiveVideoOnAirItemDaoTestImpl() {
    class Init : InitImpl()
    class Sorting : SortingImpl()
}
