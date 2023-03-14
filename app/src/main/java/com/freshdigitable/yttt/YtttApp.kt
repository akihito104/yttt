package com.freshdigitable.yttt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YtttApp : Application() {
    val googleService: GoogleService by lazy {
        GoogleService(this)
    }
}
