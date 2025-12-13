plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.android.room)
}

android {
    namespace = "com.freshdigitable.yttt.data.source.local"

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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
    packaging {
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    api(project(":common"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)

    testImplementation(libs.junit)

    androidTestImplementation(project(":common-test"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.paging.testing)
}
