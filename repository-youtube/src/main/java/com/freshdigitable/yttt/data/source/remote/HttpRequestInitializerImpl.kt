package com.freshdigitable.yttt.data.source.remote

import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.logD
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer

internal class HttpRequestInitializerImpl(
    private val credential: GoogleAccountCredential,
    private val dataStore: YouTubeAccountDataStore.Local,
) : HttpRequestInitializer {
    override fun initialize(request: HttpRequest?) {
        logD("YouTubeLiveRemoteDataSource") { "init: ${request?.url}" }
        val account = checkNotNull(dataStore.getAccount()) { "google account is null." }
        credential.selectedAccountName = account
        credential.initialize(request)
    }
}
