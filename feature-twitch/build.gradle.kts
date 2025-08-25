import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
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
                "proguard-rules.pro"
            )
        }
    }
}
android.testOptions {
    unitTests.all {
        it.useJUnitPlatform()
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
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.datatest)

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
    androidTestImplementation(libs.kotest.assertions.core)
}
