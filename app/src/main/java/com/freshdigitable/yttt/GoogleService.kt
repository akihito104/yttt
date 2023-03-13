package com.freshdigitable.yttt

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability

class GoogleService(
    private val context: Context
) {
    val googleApiAvailability: GoogleApiAvailability get() = GoogleApiAvailability.getInstance()
    val connectionStatusCode: Int
        get() = googleApiAvailability.isGooglePlayServicesAvailable(context)
}
