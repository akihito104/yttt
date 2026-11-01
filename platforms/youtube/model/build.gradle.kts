plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.yttt.kotest)
}

android {
    namespace = "com.freshdigitable.yttt.data.youtube"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":common"))

    testFixturesImplementation(project(":common"))

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":common")))

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
