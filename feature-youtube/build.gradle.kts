plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.yttt.kotest)
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
                "proguard-rules.pro",
            )
        }
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

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.mockk)
    testImplementation(libs.junit)
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
    testFixturesImplementation(testFixtures(project(":repository-youtube")))
    kspTestFixtures(libs.hilt.compiler)
    testFixturesImplementation(libs.androidx.room.runtime)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.turbine)
    testFixturesImplementation(libs.androidx.work.testing)
    testFixturesImplementation(libs.androidx.hilt.work)
    testFixturesImplementation(libs.androidx.paging.testing)
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-vintage", "kotest")
    }
}
