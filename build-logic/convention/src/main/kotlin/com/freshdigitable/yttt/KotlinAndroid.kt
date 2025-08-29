package com.freshdigitable.yttt

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val warningsAsErrors: String? by project
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> TODO("Unsupported project extension $this ${T::class}")
    }.apply {
        jvmToolchain(17)
        allWarningsAsErrors = warningsAsErrors.toBoolean()
        freeCompilerArgs.add(
            // Enable experimental coroutines APIs, including Flow
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

fun Project.configureDesugaring(commonExtension: CommonExtension<*, *, *, *, *, *>) =
    commonExtension.apply {
        compileOptions.isCoreLibraryDesugaringEnabled = true
        packaging {
            resources.excludes.add("META-INF/INDEX.LIST")
        }
        dependencies {
            add("coreLibraryDesugaring", libs.findLibrary("desugarJdkLibs").get())
        }
    }

fun Project.configureKotest(commonExtension: CommonExtension<*, *, *, *, *, *>) =
    commonExtension.apply {
        testOptions.unitTests.all {
            it.useJUnitPlatform()
        }
        dependencies {
            testImplementation(libs.findLibrary("kotest-runner-junit5"))
            testImplementation(libs.findLibrary("kotest-assertions-core"))
        }
    }
