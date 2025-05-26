plugins {
    alias(libs.plugins.yttt.android.application)
    alias(libs.plugins.yttt.hilt)
    alias(libs.plugins.licensee)
    alias(libs.plugins.oss.license.plugin)
}

android {
    namespace = "com.freshdigitable.yttt"

    defaultConfig {
        applicationId = "com.freshdigitable.yttt"
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("staging") {
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    hilt {
        enableAggregatingTask = true
    }
}

dependencies {
    implementation(project(":lib"))
    implementation(project(":common"))
    implementation(project(":common-ui"))
    implementation(project(":feature-youtube"))
    implementation(project(":feature-twitch"))

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.oss.lisence.lib)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allowUrl("https://developer.android.com/studio/terms.html")
    ignoreDependencies("junit")
    ignoreDependencies("org.hamcrest")
}
