package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeAccountRepository @Inject constructor(
    local: YouTubeAccountDataStore.Local
) : YouTubeAccountDataStore by local, AccountRepository {
    override val isTokenInvalid: Flow<Boolean?> = emptyFlow()
}
