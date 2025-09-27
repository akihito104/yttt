import com.android.build.api.dsl.LibraryExtension
import com.freshdigitable.yttt.applyAndroidLibrary
import com.freshdigitable.yttt.applyDetekt
import com.freshdigitable.yttt.applyKotlinAndroid
import com.freshdigitable.yttt.configureAndroidSdk
import com.freshdigitable.yttt.configureCompose
import com.freshdigitable.yttt.configureDesugaring
import com.freshdigitable.yttt.configureKotest
import com.freshdigitable.yttt.configureKotlin
import com.freshdigitable.yttt.libs
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
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

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (target.rootProject == target) {
            pluginManager.applyDetekt()
            val reportMerge = tasks.register<ReportMergeTask>("detektReportMerge") {
                output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.sarif"))
            }
            subprojects {
                pluginManager.applyDetekt()
                extensions.configure<DetektExtension> {
                    parallel = true
                    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
                    buildUponDefaultConfig = true
                    ignoreFailures = true
                    basePath = projectDir.absolutePath
                    autoCorrect = true
                }
                val detektTask = tasks.withType<Detekt>()
                detektTask.configureEach {
                    reports {
                        xml.required.set(false)
                        sarif.required.set(true)
                        html.required.set(true)
                        md.required.set(false)
                        txt.required.set(false)
                    }
                    finalizedBy(rootProject.tasks.withType<ReportMergeTask>().single())
                }
                reportMerge.configure {
                    input.from(detektTask.map { it.sarifReportFile })
                }
                dependencies {
                    "detektPlugins"(target.libs.findLibrary("detekt-formatting").get())
                    "detektPlugins"(target.libs.findLibrary("detekt-compose-rules").get())
                }
            }
        }
    }
}
