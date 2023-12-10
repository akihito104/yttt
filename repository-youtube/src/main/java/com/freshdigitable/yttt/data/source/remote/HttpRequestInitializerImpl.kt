package com.freshdigitable.yttt.data.source.remote

import android.util.Log
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer

internal class HttpRequestInitializerImpl(
    private val credential: GoogleAccountCredential,
    private val dataStore: AccountLocalDataSource,
) : HttpRequestInitializer {
    override fun initialize(request: HttpRequest?) {
        Log.d("YouTubeLiveRemoteDataSource", "init: ${request?.url}")
        val account = checkNotNull(dataStore.getAccount()) { "google account is null." }
        credential.selectedAccountName = account
        credential.initialize(request)
    }
}
