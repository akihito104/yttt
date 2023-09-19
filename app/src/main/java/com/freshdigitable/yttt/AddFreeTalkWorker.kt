package com.freshdigitable.yttt

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freshdigitable.yttt.data.YouTubeLiveRepository
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveVideo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AddFreeTalkWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: YouTubeLiveRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val text = inputData.uri ?: return Result.failure()
        val url = Uri.parse(text)
        if (!url.isYouTubeUri) {
            Log.d(TAG, "handleFreeTalk: ${url.scheme}, ${url.host}, ${url.pathSegments}")
            return Result.failure()
        }
        val value = url.pathSegments[1] ?: return Result.failure()
        Log.d(TAG, "handleFreeTalk: $value")
        val id = LiveVideo.Id(platform = LivePlatform.YOUTUBE, value = value)
        val v = repository.fetchVideoList(listOf(id))
        repository.addFreeChatItems(v.map { it.id })
        return Result.success()
    }

    companion object {
        @Suppress("unused")
        private val TAG = AddFreeTalkWorker::class.simpleName
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
