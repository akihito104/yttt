plugins {
    alias(libs.plugins.yttt.android.library)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.android.room)
    id("de.mannodermaus.android-junit5")
}

android {
    namespace = "com.freshdigitable.yttt.data.source.local"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments.put(
            "runnerBuilder",
            "de.mannodermaus.junit5.AndroidJUnit5Builder"
        )
        consumerProguardFiles("consumer-rules.pro")
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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
    packaging {
        resources.excludes.addAll(
            listOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
        )
    }
}
junitPlatform {
    instrumentationTests.includeExtensions.set(true)
    instrumentationTests.version.set("1.8.0")
}
dependencies {
    api(project(":common"))

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)

    testImplementation(libs.junit)

    androidTestImplementation(project(":common-test"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:5.13.1")
    androidTestImplementation("de.mannodermaus.junit5:android-test-core:1.8.0")
    androidTestRuntimeOnly("de.mannodermaus.junit5:android-test-runner:1.8.0")
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
}
