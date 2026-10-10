import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.yttt.kotest)
}

android {
    namespace = "com.freshdigitable.yttt.feature.timetable.twitch"

    defaultConfig {
        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
        consumerProguardFiles("consumer-rules.pro")

        val twitchPFile = rootProject.file("twitch.properties")
        val twitchProperties = Properties()
        if (twitchPFile.exists()) {
            FileInputStream(twitchPFile).use { twitchProperties.load(it) }
        }
        manifestPlaceholders["scheme"] =
            twitchProperties.getOrDefault("twitch_redirect_uri_scheme", "")
        manifestPlaceholders["host"] = twitchProperties.getOrDefault("twitch_redirect_uri_host", "")
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
    api(project(":common"))
    api(project(":common-ui"))
    implementation(project(":repository-twitch"))

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.paging.runtime)

    testImplementation(libs.mockk)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)

    androidTestImplementation(libs.hilt.android.testing)

    testFixturesImplementation(libs.androidx.junit)
    testFixturesImplementation(libs.androidx.espresso.core)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(testFixtures(project(":data-local-room")))
    testFixturesImplementation(project(":image-loadable-coil"))
    testFixturesImplementation(project(":common-test"))
    testFixturesImplementation(testFixtures(project(":repository-twitch")))
    testFixturesImplementation(project(":repository-appuser"))
    kspTestFixtures(libs.hilt.compiler)
    testFixturesImplementation(libs.androidx.room.runtime)
    testFixturesImplementation(libs.turbine)
    testFixturesImplementation(libs.kotest.assertions.core)
}
