import com.freshdigitable.yttt.testFixturesImplementation
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
            "\"${twitchProperties.getOrDefault("twitch_client_id", "")}\"",
        )
        buildConfigField(
            "String", "TWITCH_REDIRECT_URI",
            "\"${twitchProperties.getOrDefault("twitch_redirect_uri_scheme", "https")}://" +
                "${twitchProperties.getOrDefault("twitch_redirect_uri_host", "example.com")}\"",
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
                "proguard-rules.pro",
            )
            consumerProguardFiles("consumer-rules.pro")
        }
    }
    testFixtures {
        enable = true
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

    testFixturesImplementation(project(":common-ui"))
    testFixturesImplementation(project(":common-test"))
    testFixturesImplementation(project(":repository-appuser"))
    testFixturesImplementation(project(":image-loadable-coil"))
    testFixturesImplementation(testFixtures(project(":data-local-room")))
    testFixturesImplementation(libs.androidx.room.runtime)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.androidx.espresso.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
