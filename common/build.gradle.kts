plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.freshdigitable.yttt.data.model"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}
android.testOptions {
    unitTests.all {
        it.useJUnitPlatform()
    }
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.compose.material3)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(project(":common-test"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.datatest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
