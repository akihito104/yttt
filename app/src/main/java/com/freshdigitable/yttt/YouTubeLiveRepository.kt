package com.freshdigitable.yttt

import android.content.Context
import androidx.core.content.edit
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.Activity
import com.google.api.services.youtube.model.Subscription
import com.google.api.services.youtube.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.Period

class YouTubeLiveRepository(
    context: Context
) {
    val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(
        context.applicationContext, listOf(YouTubeScopes.YOUTUBE_READONLY),
    ).setBackOff(ExponentialBackOff())
    private val youtube = YouTube.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
    ).build()
    private val pref = context.getSharedPreferences("yttt", Context.MODE_PRIVATE)

    fun login(account: String? = null): Boolean {
        if (account != null) {
            putAccount(account)
        }
        val accountName = pref.getString(PREF_ACCOUNT_NAME, null)
        if (accountName != null) {
            credential.selectedAccountName = accountName
            return true
        }
        return false
    }

    private fun putAccount(account: String) {
        pref.edit {
            putString(PREF_ACCOUNT_NAME, account)
        }
    }

    suspend fun fetchAllSubscribe(
        maxResult: Long = 30,
    ): List<Subscription> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.Subscriptions().list(listOf("snippet"))
                    .setMine(true)
                    .setMaxResults(maxResult)
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        )
    }

    suspend fun fetchActivitiesList(
        channelId: String,
        publishedAfter: Instant = Instant.now().minus(activityMaxPeriod),
        maxResult: Long = 30,
    ): List<Activity> = withContext(Dispatchers.IO) {
        fetchAllItems(
            fetcher = { token ->
                youtube.activities().list(listOf("snippet", "contentDetails"))
                    .setChannelId(channelId)
                    .setMaxResults(maxResult)
                    .setPublishedAfter(publishedAfter.toString())
                    .setPageToken(token)
                    .execute()
            },
            getItems = { items },
            getNextToken = { nextPageToken },
        )
    }

    suspend fun fetchVideoList(
        ids: Collection<String>,
    ): List<Video> = withContext(Dispatchers.IO) {
        ids.chunked(50).flatMap {
            youtube.videos().list(listOf("snippet", "liveStreamingDetails"))
                .setId(it)
                .execute()
                .items
        }
    }

    private fun <T, E> fetchAllItems(
        fetcher: (String?) -> T,
        getItems: T.() -> List<E>,
        getNextToken: T.() -> String?,
    ): List<E> {
        var token: String? = null
        val res = mutableListOf<E>()
        do {
            val response = fetcher(token)
            res.addAll(response.getItems())
            token = response.getNextToken()
        } while (token != null)
        return res
    }

    companion object {
        private const val PREF_ACCOUNT_NAME = "accountName"
        private val activityMaxPeriod = Period.ofDays(7)
    }
}
