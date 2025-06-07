package com.freshdigitable.yttt

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    pluginManager.applyComposeCompiler()

    commonExtension.apply {
        buildFeatures.compose = true

        dependencies {
            val bom = libs.androidxComposeBom
            implementation(platform(bom))
            androidTestImplementation(platform(bom))
            implementation(libs.androidxComposeUiToolingPreview)
            debugImplementation(libs.androidxComposeUiTooling)
        }
    }
}
