import com.android.build.api.dsl.LibraryExtension
import com.freshdigitable.yttt.androidTestImplementation
import com.freshdigitable.yttt.androidxComposeBom
import com.freshdigitable.yttt.androidxComposeUiTooling
import com.freshdigitable.yttt.androidxComposeUiToolingPreview
import com.freshdigitable.yttt.applyAndroidLibrary
import com.freshdigitable.yttt.applyComposeCompiler
import com.freshdigitable.yttt.applyKotlinAndroid
import com.freshdigitable.yttt.configureAndroidSdk
import com.freshdigitable.yttt.configureKotlin
import com.freshdigitable.yttt.debugImplementation
import com.freshdigitable.yttt.implementation
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            applyAndroidLibrary()
            applyKotlinAndroid()
        }
        extensions.configure<LibraryExtension> {
            configureAndroidSdk(this)
            configureKotlin<KotlinAndroidProjectExtension>()
        }
    }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        AndroidLibraryConventionPlugin().apply(this)

        pluginManager.applyComposeCompiler()

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true

            dependencies {
                val bom = libs.androidxComposeBom
                implementation(platform(bom))
                androidTestImplementation(platform(bom))
                implementation(libs.androidxComposeUiToolingPreview)
                debugImplementation(libs.androidxComposeUiTooling)
            }
        }
    }
}
