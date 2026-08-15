package com.freshdigitable.yttt.data.source.local.db

import org.robolectric.annotation.SQLiteMode

@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TwitchPagingSourceImplTest : TwitchPagingSourceImplTestImpl()
