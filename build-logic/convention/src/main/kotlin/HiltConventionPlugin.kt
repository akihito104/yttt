import com.freshdigitable.yttt.applyHiltAndroid
import com.freshdigitable.yttt.applyKsp
import com.freshdigitable.yttt.hiltAndroid
import com.freshdigitable.yttt.hiltCompiler
import com.freshdigitable.yttt.implementation
import com.freshdigitable.yttt.ksp
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.applyKsp()
        dependencies {
            ksp(libs.hiltCompiler)
        }

        pluginManager.withPlugin("com.android.base") {
            pluginManager.applyHiltAndroid()
            dependencies {
                implementation(libs.hiltAndroid)
            }
        }
    }
}
