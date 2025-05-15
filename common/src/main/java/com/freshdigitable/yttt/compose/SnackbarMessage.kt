package com.freshdigitable.yttt.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import com.freshdigitable.yttt.data.source.NetworkResponse
import kotlinx.coroutines.channels.SendChannel

data class SnackbarMessage(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
) : SnackbarVisuals {
    companion object {
        fun fromThrowable(throwable: Throwable): SnackbarMessage {
            val message = if (throwable is NetworkResponse.Exception) {
                if (throwable.isQuotaExceeded) {
                    "we have reached the usage limit. please try again later."
                } else if (throwable.statusCode in 400..499) {
                    "we have encountered an error. please contact to app developer."
                } else if (throwable.statusCode in 500..599) {
                    "service temporarily unavailable. please try again later."
                } else {
                    "unknown error"
                }
            } else {
                "unknown error"
            }
            return SnackbarMessage(message)
        }
    }
}

suspend inline fun <T> Result<T>.onFailureWithSnackbarMessage(channel: SendChannel<SnackbarVisuals>): Result<T> =
    onFailure { channel.send(SnackbarMessage.fromThrowable(it)) }
