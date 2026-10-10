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
    api(libs.androidx.junit)
    api(libs.androidx.test.runner)
    api(libs.kotlinx.coroutines.test)
    api(libs.hilt.android.testing)
    implementation(libs.kotest.assertions.core)
    api(libs.androidx.paging.runtime)
    api(libs.androidx.paging.testing)
    implementation(libs.mockwebserver3)
    implementation(libs.okhttp.logging.interceptor)
}

tasks.withType<Test> {
    failOnNoDiscoveredTests = false
}
