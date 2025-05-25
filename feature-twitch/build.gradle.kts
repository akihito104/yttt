@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.freshdigitable.yttt.feature.timetable.twitch"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.extension.get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":common"))
    api(project(":common-ui"))
    implementation(project(":repository-twitch"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.paging.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":data-local-room"))
    androidTestImplementation(project(":image-loadable-coil"))
    androidTestImplementation(project(":common-test"))
    androidTestImplementation(project(":repository-appuser"))
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.truth)

}
