package com.freshdigitable.yttt.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freshdigitable.yttt.data.YouTubeFacade
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.logD
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
internal class AddFreeTalkWorker @AssistedInject constructor(
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

class AddStreamActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        logD { "onCreate: $intent" }
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        startActivity(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        AddFreeTalkWorker.enqueue(this, text)
    }
}
