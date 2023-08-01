package com.freshdigitable.yttt.data

import com.freshdigitable.yttt.data.source.AccountDataStore
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    localDataStore: AccountLocalDataSource,
) : AccountDataStore by localDataStore {
    fun hasAccount(): Boolean = getAccount() != null
}
