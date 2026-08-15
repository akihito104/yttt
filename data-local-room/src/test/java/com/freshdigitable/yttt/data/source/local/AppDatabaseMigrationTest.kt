package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppDatabaseMigrationTest : AppDatabaseMigrationTestImpl() {
    class From13To14 : From13To14Impl()
    class From15To16 : From15To16Impl()
    class From18To19 : From18To19Impl()
    class From23To24 : From23To24Impl()
    class From25To26 : From25To26Impl()
}
