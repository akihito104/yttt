import java.util.Locale

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
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("test").assets.directories.add("$projectDir/schemas")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
    packaging {
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("UnitTestKotlin") || name.contains("TestFixturesKotlin")) {
        name.removePrefix("compile")
            .removeSuffix("Kotlin")
            .let { n -> n.replaceFirstChar { it.lowercase(Locale.ROOT) } }

        val mainCompilation = kotlin.target.compilations.findByName("main")
        if (mainCompilation != null) {
            friendPaths.from(mainCompilation.output.classesDirs)
        }
    }
}

dependencies {
    api(project(":common"))

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.paging)

    kspTestFixtures(libs.hilt.compiler)
    testFixturesImplementation(libs.hilt.android.testing)
    testFixturesImplementation(project(":common-test"))
    testFixturesImplementation(libs.androidx.junit)
    testFixturesImplementation(libs.androidx.test.runner)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(libs.turbine)
    testFixturesImplementation(libs.androidx.room.testing)
    testFixturesImplementation(libs.androidx.espresso.core)
    testFixturesCompileOnly(libs.androidx.paging.runtime)
    testFixturesImplementation(libs.androidx.paging.testing)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
