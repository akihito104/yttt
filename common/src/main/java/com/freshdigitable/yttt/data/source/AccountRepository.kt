package com.freshdigitable.yttt.data.source

import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun hasAccount(): Boolean
    val isTokenInvalid: Flow<Boolean?>
}
