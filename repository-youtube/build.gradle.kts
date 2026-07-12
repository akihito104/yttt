plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
}

android {
    namespace = "com.freshdigitable.yttt.data.source.remote"

    defaultConfig {
        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.youtube) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.okhttp)
    implementation(libs.play.services.base)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)

    testFixturesImplementation(project(":common-test"))
    kspTestFixtures(libs.hilt.compiler)
    testFixturesImplementation(libs.hilt.android.testing)
    testFixturesImplementation(libs.androidx.paging.testing)
    testFixturesImplementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }

    testImplementation(libs.junit)
    androidTestImplementation(project(":common-test"))
    androidTestImplementation(project(":image-loadable-coil"))
    androidTestImplementation(testFixtures(project(":data-local-room")))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.androidx.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.paging.testing)
}
