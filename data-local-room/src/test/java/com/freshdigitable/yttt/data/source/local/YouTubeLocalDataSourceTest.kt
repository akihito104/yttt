package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

class YouTubeLocalDataSourceTest : YouTubeLocalDataSourceTestImpl() {
    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class Init : InitImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class SimpleFetchVideo : SimpleFetchVideoImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class UpdatePlaylistWithItems : UpdatePlaylistWithItemsImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class SimpleFetchPlaylistWithItems : SimpleFetchPlaylistWithItemsImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class CleanUp : CleanUpImpl()

    @Config(sdk = [26, 33])
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    class WhenSubscriptionIsRemoved : WhenSubscriptionIsRemovedImpl()
}
