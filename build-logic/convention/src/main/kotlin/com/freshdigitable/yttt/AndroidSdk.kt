package com.freshdigitable.yttt

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

fun Project.configureAndroidSdk(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = 36

        defaultConfig {
            (this as? ApplicationDefaultConfig)?.targetSdk = 36
            minSdk = 26
        }
    }
}
