package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
class YouTubeLocalDataSourceTest : YouTubeLocalDataSourceTestImpl() {
    class Init : InitImpl()
    class SimpleFetchVideo : SimpleFetchVideoImpl()
    class UpdatePlaylistWithItems : UpdatePlaylistWithItemsImpl()
    class SimpleFetchPlaylistWithItems : SimpleFetchPlaylistWithItemsImpl()
    class CleanUp : CleanUpImpl()
    class WhenSubscriptionIsRemoved : WhenSubscriptionIsRemovedImpl()
}
