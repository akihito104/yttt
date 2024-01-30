package com.freshdigitable.yttt

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
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
