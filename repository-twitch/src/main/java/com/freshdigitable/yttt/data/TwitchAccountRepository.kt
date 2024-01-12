package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TwitchAccountRepository @Inject constructor(
    local: TwitchAccountDataStore.Local
) : TwitchAccountDataStore by local
