package com.freshdigitable.yttt.data.source.local

import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@Config(sdk = [26, 33])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LiveLocalPagingSourceTest : LiveLocalPagingSourceTestImpl()
