import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
}

android {
    namespace = "com.freshdigitable.yttt.data"

    defaultConfig {
        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
        consumerProguardFiles("consumer-rules.pro")

        val twitchPFile = rootProject.file("twitch.properties")
        val twitchProperties = Properties()
        if (twitchPFile.exists()) {
            FileInputStream(twitchPFile).use { twitchProperties.load(it) }
        }
        buildConfigField(
            "String", "TWITCH_CLIENT_ID",
            "\"${twitchProperties.getOrDefault("twitch_client_id", "")}\""
        )
        buildConfigField(
            "String", "TWITCH_REDIRECT_URI",
            "\"${twitchProperties.getOrDefault("twitch_redirect_uri", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true
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

dependencies {
    api(project(":common"))
    implementation(project(":data-local-room"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    ksp(libs.retrofit.responseTypeKeeper)
    implementation(libs.converter.gson)

    testImplementation(libs.junit)
    androidTestImplementation(project(":repository-appuser"))
    androidTestImplementation(project(":image-loadable-coil"))
    androidTestImplementation(project(":common-test"))
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.paging.testing)
    androidTestImplementation(libs.androidx.espresso.core)
}
