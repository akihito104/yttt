package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeAccountRepository @Inject constructor(
    local: YouTubeAccountDataStore.Local
) : YouTubeAccountDataStore by local

class TwitchAccountRepository @Inject constructor(
    local: TwitchAccountDataStore.Local
) : TwitchAccountDataStore by local
