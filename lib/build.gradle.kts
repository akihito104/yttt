plugins {
    alias(libs.plugins.yttt.android.library.compose)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.yttt.kotest)
}

android {
    namespace = "com.freshdigitable.yttt.lib"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    packaging {
        resources {
            excludes.addAll(listOf("dependencies.txt"))
        }
    }
    hilt {
        enableAggregatingTask = true
    }
}

dependencies {
    api(project(":common"))
    api(project(":common-ui"))
    implementation(project(":repository-appuser"))
    implementation(project(":image-loadable-coil"))

    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
