package com.freshdigitable.yttt

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

fun Project.configureAndroidSdk(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig {
            (this as? ApplicationDefaultConfig)?.targetSdk = 35
            minSdk = 26
        }
    }
}
