import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.freshdigitable.yttt.androidTestUtil
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class TestingPlugin : Plugin<Project> {
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
                subprojectsWithJacoco.forEach { s -> // fixme: off orchestrator to avoid error
                    if (s.pluginManager.hasPlugin("com.android.application")) {
                        s.configure<BaseAppModuleExtension> {
                            s.configureOrchestrator(this, isEnabled = false)
                        }
                    } else if (s.pluginManager.hasPlugin("com.android.library")) {
                        s.configure<LibraryExtension> {
                            s.configureOrchestrator(this, isEnabled = false)
                        }
                    }
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
        } else {
            configureTest()
        }
    }

    companion object {
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

        private fun Project.configureTest(isOrchestratorEnabled: Boolean = true) {
            if (pluginManager.hasPlugin("com.android.application")) {
                configure<BaseAppModuleExtension> {
                    configureCoverage(this)
                    configureOrchestrator(this, isEnabled = isOrchestratorEnabled)
                }
            } else if (pluginManager.hasPlugin("com.android.library")) {
                configure<LibraryExtension> {
                    configureCoverage(this)
                    configureOrchestrator(this, isEnabled = isOrchestratorEnabled)
                }
            }
        }

        private fun Project.configureCoverage(
            commonExtension: CommonExtension<*, *, *, *, *, *>,
        ) = with(commonExtension) {
            val hasUnitTest = sourceSets.getByName("test").java.directories.any { hasSourceFile(it) }
            val hasAndroidTest = sourceSets.getByName("androidTest").java.directories.any { hasSourceFile(it) }
            buildTypes {
                getByName("debug") {
                    enableUnitTestCoverage = hasUnitTest
                    enableAndroidTestCoverage = hasAndroidTest
                }
            }
        }

        private fun Project.hasSourceFile(dir: String): Boolean = layout.projectDirectory.dir(dir).asFile.walkTopDown()
            .any { it.name.endsWith(".kt") || it.name.endsWith(".java") }

        private fun Project.configureOrchestrator(
            commonExtension: CommonExtension<*, *, *, *, *, *>,
            isEnabled: Boolean = true,
        ) = with(commonExtension) {
            defaultConfig {
                val args = if (isEnabled) {
                    mapOf(
                        "clearPackageData" to "true",
                        "useTestStorageService" to "true",
                        "disableAnalytics" to "true",
                    )
                } else {
                    emptyMap()
                }
                testInstrumentationRunnerArguments.putAll(args)
            }
            testOptions {
                execution = if (isEnabled) "ANDROIDX_TEST_ORCHESTRATOR" else "HOST"
            }
            dependencies {
                androidTestUtil(libs.findLibrary("androidx-test-orchestrator"))
                androidTestUtil(libs.findLibrary("androidx-test-services"))
            }
        }
    }
}
