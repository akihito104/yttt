import com.android.build.api.dsl.LibraryExtension
import com.freshdigitable.yttt.applyAndroidLibrary
import com.freshdigitable.yttt.applyKotlinAndroid
import com.freshdigitable.yttt.configureAndroidSdk
import com.freshdigitable.yttt.configureCompose
import com.freshdigitable.yttt.configureDesugaring
import com.freshdigitable.yttt.configureKotest
import com.freshdigitable.yttt.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
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
            configureDesugaring(this)
        }
    }
}

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        AndroidLibraryConventionPlugin().apply(this)

        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}

class KotestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure<LibraryExtension> {
            configureKotest(this)
        }
    }
}
