plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
}

android {
    namespace = "com.freshdigitable.yttt.feature.timetable.youtube"

    defaultConfig {
        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
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
    implementation(project(":repository-youtube"))
    implementation(project(":repository-appuser"))

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.accompanist.permissions)
    implementation(libs.play.services.base)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
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
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.work.testing)
}
