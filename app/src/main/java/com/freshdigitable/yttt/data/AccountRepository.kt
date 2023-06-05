package com.freshdigitable.yttt.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val pref = context.getSharedPreferences("yttt", Context.MODE_PRIVATE)
    val googleAccount: Flow<String?> = callbackFlow {
        val listener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PREF_ACCOUNT_NAME) {
                val string = sharedPreferences?.getString(PREF_ACCOUNT_NAME, null)
                trySendBlocking(string)
            }
        }
        pref.registerOnSharedPreferenceChangeListener(listener)
        send(getAccount())
        awaitClose { pref.unregisterOnSharedPreferenceChangeListener(listener) }
    }
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

    fun getTwitchToken(): String? = pref.getString(PREF_TWITCH_TOKEN, null)
    fun putTwitchToken(token: String) {
        pref.edit {
            putString(PREF_TWITCH_TOKEN, token)
        }
    }

    companion object {
        private const val PREF_ACCOUNT_NAME = "accountName"
        private const val PREF_TWITCH_TOKEN = "twitchToken"
        fun AccountRepository.getNewChooseAccountIntent(): Intent =
            credential.newChooseAccountIntent()
    }
}
