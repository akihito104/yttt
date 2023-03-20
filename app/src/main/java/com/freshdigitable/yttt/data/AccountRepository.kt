package com.freshdigitable.yttt.data

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val pref = context.getSharedPreferences("yttt", Context.MODE_PRIVATE)
    val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
        context, listOf(YouTubeScopes.YOUTUBE_READONLY),
    ).setBackOff(ExponentialBackOff())

    fun getAccount(): String? = pref.getString(PREF_ACCOUNT_NAME, null)

    fun hasAccount(): Boolean = getAccount() != null

    fun putAccount(account: String) {
        pref.edit {
            putString(PREF_ACCOUNT_NAME, account)
        }
    }

    fun setSelectedAccountName(account: String) {
        credential.selectedAccountName = account
    }

    companion object {
        private const val PREF_ACCOUNT_NAME = "accountName"
        fun AccountRepository.getNewChooseAccountIntent(): Intent =
            credential.newChooseAccountIntent()
    }
}
