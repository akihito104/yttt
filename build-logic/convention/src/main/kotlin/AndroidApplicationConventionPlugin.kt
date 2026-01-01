import com.android.build.api.dsl.ApplicationExtension
import com.freshdigitable.yttt.applyAndroidApplication
import com.freshdigitable.yttt.applyKotlinAndroid
import com.freshdigitable.yttt.configureAndroidSdk
import com.freshdigitable.yttt.configureCompose
import com.freshdigitable.yttt.configureDesugaring
import com.freshdigitable.yttt.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            applyAndroidApplication()
            applyKotlinAndroid()
            apply("yttt.testing")
        }

        extensions.configure<ApplicationExtension> {
            configureAndroidSdk(this)
            configureKotlin<KotlinAndroidProjectExtension>()
            testOptions.animationsDisabled = true
            configureDesugaring(this)
        }
    }
}

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        AndroidApplicationConventionPlugin().apply(this)

        extensions.configure<ApplicationExtension> {
            configureCompose(this)
        }
    }
}
