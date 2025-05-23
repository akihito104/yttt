@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.freshdigitable.yttt.data.source.remote"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "com.freshdigitable.yttt.test.CustomTestRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.youtube) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.play.services.base)
    implementation(libs.play.services.auth)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(project(":common-test"))
    androidTestImplementation(project(":image-loadable-coil"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
}
