import java.io.FileInputStream
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.freshdigitable.yttt.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "com.freshdigitable.yttt.data.CustomTestRunner"
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":common"))
    implementation(project(":data-local-room"))

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    ksp(libs.retrofit.responseTypeKeeper)
    implementation(libs.converter.gson)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(project(":repository-appuser"))
    androidTestImplementation(project(":image-loadable-coil"))
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.espresso.core)
}
