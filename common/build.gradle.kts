plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.yttt.kotest)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.freshdigitable.yttt.data.model"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)
    implementation(libs.androidx.paging.runtime)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    androidTestImplementation(libs.androidx.espresso.core)

    testFixturesApi(libs.androidx.junit)
    testFixturesApi(libs.androidx.test.runner)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.hilt.android.testing)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesApi(libs.androidx.paging.runtime)
    testFixturesApi(libs.androidx.paging.testing)
    testFixturesImplementation(libs.mockwebserver3)
    testFixturesImplementation(libs.okhttp.logging.interceptor)
}
