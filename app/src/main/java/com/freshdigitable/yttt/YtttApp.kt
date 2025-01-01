package com.freshdigitable.yttt

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import com.freshdigitable.yttt.compose.OssLicenseNavigationQualifier
import com.freshdigitable.yttt.compose.navigation.NavActivity
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@HiltAndroidApp
class YtttApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    @Inject
    lateinit var appLoggerSetup: Set<@JvmSuppressWildcards AppLoggerSetup>

    @Inject
    lateinit var appPerformanceSetup: Set<@JvmSuppressWildcards AppPerformanceSetup>

    @Inject
    lateinit var imageLoaderViewSetup: ImageLoaderViewSetup

    override fun onCreate() {
        super.onCreate()
        appLoggerSetup.forEach { it() }
        appPerformanceSetup.forEach { it() }
        imageLoaderViewSetup()
    }
}

object OssLicenseNav : NavActivity(
    path = "oss_license",
    activityClass = OssLicensesMenuActivity::class,
    action = Intent.ACTION_VIEW,
)

@Module
@InstallIn(SingletonComponent::class)
interface OssLicenseNavigation {
    companion object {
        @Provides
        @OssLicenseNavigationQualifier
        fun provideOssLicenseNav(): NavActivity = OssLicenseNav
    }
}
