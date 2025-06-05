package com.freshdigitable.yttt.feature.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.lifecycleScope
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freshdigitable.yttt.logD
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@HiltWorker
internal class AddStreamWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val addStream: AddStreamUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val data = AddStreamUseCase.Input.fromWorkerData(inputData) ?: return Result.failure()
        logD { "handlePathParameter: $data" }
        val res = addStream(data)
        return if (res.isSuccess) Result.success() else Result.failure()
    }

    companion object {
        fun enqueue(context: Context, data: AddStreamUseCase.Input): Flow<WorkInfo?> {
            val workReq = OneTimeWorkRequestBuilder<AddStreamWorker>()
                .setInputData(data.toWorkerData())
                .build()
            return WorkManager.getInstance(context).run {
                enqueue(workReq)
                getWorkInfoByIdFlow(workReq.id)
            }
        }

        private const val KEY_ID = "id"
        private const val KEY_FREE_CHAT = "free_chat"

        private fun AddStreamUseCase.Input.toWorkerData(): Data = Data.Builder()
            .putString(KEY_ID, id.value)
            .putBoolean(KEY_FREE_CHAT, isFreeChat)
            .build()

        private fun AddStreamUseCase.Input.Companion.fromWorkerData(data: Data): AddStreamUseCase.Input? {
            val id = data.getString(KEY_ID) ?: return null
            val isFreeChat = data.getBoolean(KEY_FREE_CHAT, false)
            return AddStreamUseCase.Input(id = id, isFreeChat = isFreeChat)
        }
    }
}

open class AddStreamActivity : AppCompatActivity() {
    private var job: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        logD { "onCreate: $intent" }
        super.onCreate(savedInstanceState)
        val data = handleIntent(intent)
        if (data == null) {
            Toast.makeText(this@AddStreamActivity, "Failed to add stream", Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        job = lifecycleScope.launch {
            val info = AddStreamWorker.enqueue(this@AddStreamActivity, data)
                .firstOrNull { it?.state?.isFinished == true }
            if (info?.state == WorkInfo.State.SUCCEEDED) {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                startActivity(intent)
            } else {
                Toast.makeText(this@AddStreamActivity, "Failed to add stream", Toast.LENGTH_SHORT)
                    .show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        job = null
    }

    internal open fun handleIntent(intent: Intent): AddStreamUseCase.Input? =
        AddStreamUseCase.Input.fromIntent(intent, false)

    companion object {
        internal fun AddStreamUseCase.Input.Companion.fromIntent(
            intent: Intent,
            isFreeChat: Boolean,
        ): AddStreamUseCase.Input? {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return create(url.toUri(), isFreeChat)
        }
    }
}

class AddFreeChatActivity : AddStreamActivity() {
    override fun handleIntent(intent: Intent): AddStreamUseCase.Input? =
        AddStreamUseCase.Input.fromIntent(intent, true)
}
