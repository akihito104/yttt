package com.freshdigitable.yttt

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.model.YouTubeVideo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AddFreeTalkWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val facade: YouTubeFacade,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val text = inputData.uri ?: return Result.failure()
        val url = text.toUri()
        if (!url.isYouTubeUri) {
            logD { "handleFreeTalk: ${url.scheme}, ${url.host}, ${url.pathSegments}" }
            return Result.failure()
        }
        val value = url.pathSegments[1] ?: return Result.failure()
        logD { "handleFreeTalk: $value" }
        val id = YouTubeVideo.Id(value)
        facade.addFreeChatFromWorker(id)
        return Result.success()
    }

    companion object {
        private const val KEY_URI = "uri"

        fun enqueue(context: Context, uri: String) {
            val workReq = OneTimeWorkRequestBuilder<AddFreeTalkWorker>()
                .setInputData(getData(uri))
                .build()
            WorkManager.getInstance(context).enqueue(workReq)
        }

        private fun getData(uri: String): Data = Data.Builder()
            .putString(KEY_URI, uri)
            .build()

        private val Data.uri: String? get() = getString(KEY_URI)
        private val Uri.isYouTubeUri: Boolean
            get() = scheme == "https" && host?.endsWith("youtube.com") == true
                && pathSegments.firstOrNull() == "live"
    }
}
