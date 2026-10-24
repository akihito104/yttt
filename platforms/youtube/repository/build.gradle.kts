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

    testFixturesImplementation(testFixtures(project(":common")))
    testFixturesImplementation(project(":image-loadable-coil"))
    testFixturesImplementation(testFixtures(project(":data-local-room")))
    kspTestFixtures(libs.hilt.compiler)
    testFixturesApi(libs.hilt.android.testing)
    testFixturesImplementation(libs.androidx.paging.testing)
    testFixturesImplementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    testFixturesImplementation(libs.google.api.services.youtube) {
        exclude(group = "org.apache.httpcomponents")
    }
    testFixturesImplementation(libs.androidx.junit)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.androidx.espresso.core)

    testImplementation(libs.robolectric)
}
