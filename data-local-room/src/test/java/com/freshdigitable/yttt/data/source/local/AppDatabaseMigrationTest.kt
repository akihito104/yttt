package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

class AppDatabaseMigrationTest : AppDatabaseMigrationTestImpl() {
    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class From13To14 : From13To14Impl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class From15To16 : From15To16Impl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class From18To19 : From18To19Impl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class From23To24 : From23To24Impl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class From25To26 : From25To26Impl()
}
