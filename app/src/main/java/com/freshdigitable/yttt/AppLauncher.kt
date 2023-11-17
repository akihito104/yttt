package com.freshdigitable.yttt

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(intent: Intent) {
        context.startActivity(intent)
    }
}

class LaunchAppWithUrlUseCase @Inject constructor(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        appLauncher(intent)
    }
}
