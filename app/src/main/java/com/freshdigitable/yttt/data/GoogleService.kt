package com.freshdigitable.yttt.data

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GoogleService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val googleApiAvailability: GoogleApiAvailability
        get() = GoogleApiAvailability.getInstance()

    fun getConnectionStatusCode(): Int =
        googleApiAvailability.isGooglePlayServicesAvailable(context)

    fun isUserResolvableError(statusCode: Int): Boolean =
        googleApiAvailability.isUserResolvableError(statusCode)
}
