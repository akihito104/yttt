import com.android.build.api.dsl.CommonExtension
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class JacocoPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("jacoco")

        val jacocoVersion = libs.findVersion("jacoco").map { it.toString() }.orElse("0.8.13")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = jacocoVersion
        }

        if (this == rootProject) {
            tasks.register<JacocoReport>("jacocoFullReport") {
                group = "Reporting"
                description = "Generates a merged JaCoCo coverage report from all subprojects."

                val subprojectsWithJacoco = subprojects.filter { subproject ->
                    subproject.pluginManager.hasPlugin("yttt.jacoco") || subproject.pluginManager.hasPlugin("jacoco")
                }

                subprojectsWithJacoco.forEach { s ->
                    val tasks = s.tasks.matching {
                        it.name.startsWith("create") && it.name.endsWith("TestCoverageReport")
                    }
                    dependsOn(tasks)
                }

                val classDirs = subprojectsWithJacoco.map { subproject ->
                    subproject.fileTree(subproject.layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                        exclude(fileFilter)
                    } + subproject.fileTree(subproject.layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                        exclude(fileFilter)
                    }
                }

                val sourceDirs = subprojectsWithJacoco.map { subproject ->
                    listOf(
                        "${subproject.projectDir}/src/main/java",
                        "${subproject.projectDir}/src/main/kotlin",
                    )
                }.flatten()

                val execData = fileTree(rootDir) {
                    include("**/outputs/unit_test_code_coverage/**/*.exec")
                    include("**/outputs/code_coverage/**/*.ec")
                    include("**/outputs/connected_android_test_additional_output/**/*.ec")
                    include("**/intermediates/code_coverage/**/*.ec")
                    include("**/jacoco/*.exec")
                }

                sourceDirectories.setFrom(files(sourceDirs))
                classDirectories.setFrom(files(classDirs))
                executionData.setFrom(execData)

                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }

    private val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*Hilt*.*", "**/dagger/hilt/internal/*", "**/hilt_aggregated_deps/*",
        "**/*_Factory.*", "**/*_MembersInjector.*", "**/*Module_*",
        "**/*_Dao_Impl.*", "**/*AppDatabase_Impl.*",
        "com/freshdigitable/yttt/**/di/*",
        "com/freshdigitable/yttt/di/*",
        "com/freshdigitable/yttt/test/*",
    )

    companion object {
        inline fun <reified T : CommonExtension<*, *, *, *, *, *>> Project.configureCoverage() = configure<T> {
            val hasUnitTest = sourceSets.getByName("test").java.directories.any { dir ->
                layout.projectDirectory.dir(dir).asFile.walkTopDown()
                    .any { it.name.endsWith(".kt") || it.name.endsWith(".java") }
            }
            val hasAndroidTest = sourceSets.getByName("androidTest").java.directories.any { dir ->
                layout.projectDirectory.dir(dir).asFile.walkTopDown()
                    .any { it.name.endsWith(".kt") || it.name.endsWith(".java") }
            }
            buildTypes {
                getByName("debug") {
                    enableUnitTestCoverage = hasUnitTest
                    enableAndroidTestCoverage = hasAndroidTest
                }
            }
        }
    }
}
