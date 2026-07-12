plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
}

android {
    namespace = "com.freshdigitable.yttt.test"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.hilt.android.testing)
    implementation(libs.kotest.assertions.core)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.testing)
    implementation(libs.mockwebserver3)
    implementation(libs.okhttp.logging.interceptor)
}
