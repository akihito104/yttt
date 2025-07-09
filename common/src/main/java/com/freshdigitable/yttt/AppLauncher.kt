package com.freshdigitable.yttt

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.freshdigitable.yttt.LauncherOption.Companion.addLauncherOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    operator fun invoke(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class LaunchAppWithUrlUseCase @Inject constructor(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(url: String, applier: Intent.() -> Unit = {}) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply(applier)
        appLauncher(intent)
    }
}

fun Activity.startLauncherActivity(option: LauncherOption, intent: Intent.() -> Unit = {}) {
    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        intent(this)
        addLauncherOption(option)
    } ?: return
    startActivity(intent)
}


enum class LauncherOption {
    ON_FINISH_OAUTH, NO_SPLASH,
    ;

    companion object {
        private const val EXTRA_LAUNCHER_OPTION = "launcher_option"
        internal fun Intent.addLauncherOption(option: LauncherOption) {
            putExtra(EXTRA_LAUNCHER_OPTION, option.name)
        }

        val Intent.launcherOption: LauncherOption?
            get() {
                val name = getStringExtra(EXTRA_LAUNCHER_OPTION) ?: return null
                return LauncherOption.valueOf(name)
            }
    }
}
