package com.freshdigitable.yttt.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val pref = context.getSharedPreferences("yttt", Context.MODE_PRIVATE)

    fun getAccount(): String? = pref.getString(PREF_ACCOUNT_NAME, null)

    fun putAccount(account: String) {
        pref.edit {
            putString(PREF_ACCOUNT_NAME, account)
        }
    }

    companion object {
        private const val PREF_ACCOUNT_NAME = "accountName"
    }
}
