package com.freshdigitable.yttt

import android.app.Application

class YtttApp : Application() {
    val youTubeLiveRepository: YouTubeLiveRepository by lazy {
        YouTubeLiveRepository(this)
    }
    val googleService: GoogleService by lazy {
        GoogleService(this)
    }
}
