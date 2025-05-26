import com.android.build.api.dsl.LibraryExtension
import com.freshdigitable.yttt.configureAndroidSdk
import com.freshdigitable.yttt.configureKotlin
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("org.jetbrains.kotlin.android")
        }
        extensions.configure<LibraryExtension> {
            configureAndroidSdk(this)
            configureKotlin<KotlinAndroidProjectExtension>()
        }
    }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    private val libraryPlugin = AndroidLibraryConventionPlugin()
    override fun apply(target: Project) = with(target) {
        libraryPlugin.apply(this)
        with(pluginManager) {
            apply("org.jetbrains.kotlin.plugin.compose")
        }
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
