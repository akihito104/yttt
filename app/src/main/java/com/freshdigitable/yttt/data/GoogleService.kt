package com.freshdigitable.yttt.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GoogleService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val googleApiAvailability: GoogleApiAvailability
        get() = GoogleApiAvailability.getInstance()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus?>(null)
    val connectionStatus: StateFlow<ConnectionStatus?> = _connectionStatus

    fun getConnectionStatus(): ConnectionStatus {
        val code = googleApiAvailability.isGooglePlayServicesAvailable(context)
        val status = if (code == ConnectionResult.SUCCESS) {
            ConnectionStatus.Succeeded
        } else {
            ConnectionStatus.Failure(
                isUserRecoverable = googleApiAvailability.isUserResolvableError(code),
                code = code,
            )
        }
        _connectionStatus.value = status
        return status
    }

    sealed class ConnectionStatus {
        object Succeeded : ConnectionStatus()
        data class Failure(val isUserRecoverable: Boolean, val code: Int) : ConnectionStatus()
    }
}
