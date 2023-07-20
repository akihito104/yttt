package com.freshdigitable.yttt.data

import android.content.Context
import android.content.Intent
import com.freshdigitable.yttt.data.source.AccountDataStore
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext context: Context,
    localDataStore: AccountLocalDataSource,
) : AccountDataStore by localDataStore {
    val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
        context, listOf(YouTubeScopes.YOUTUBE_READONLY),
    ).setBackOff(ExponentialBackOff())

    fun hasAccount(): Boolean = getAccount() != null

    fun setSelectedAccountName(account: String) {
        credential.selectedAccountName = account
    }

    companion object {
        fun AccountRepository.getNewChooseAccountIntent(): Intent =
            credential.newChooseAccountIntent()
    }
}
