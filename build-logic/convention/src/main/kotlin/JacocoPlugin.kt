import com.android.build.api.dsl.CommonExtension
import com.freshdigitable.yttt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class JacocoPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("jacoco")

        extensions.configure<JacocoPluginExtension> {
            toolVersion = libs.findVersion("jacoco").map { it.toString() }.orElse("0.8.13")
        }

        if (this == rootProject) {
            // ルートプロジェクトの場合、全サブプロジェクトのレポートを統合するタスクを作成
            tasks.register<JacocoReport>("jacocoFullReport") {
                group = "Reporting"
                description = "Generates a merged JaCoCo coverage report from all subprojects."

                val subprojectsWithJacoco = subprojects.filter { subproject ->
                    subproject.pluginManager.hasPlugin("yttt.jacoco") || subproject.pluginManager.hasPlugin("jacoco")
                }
                // 各プロジェクトのテストタスクに依存させる
                dependsOn(subprojectsWithJacoco.flatMap { it.tasks.withType<Test>() })

                val classDirs = subprojectsWithJacoco.map { subproject ->
                    subproject.fileTree(subproject.layout.buildDirectory.dir("intermediates/javac/debug")) {
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

                val execData = subprojectsWithJacoco.map { subproject ->
                    subproject.fileTree(subproject.layout.buildDirectory) {
                        include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
                        include("jacoco/testDebugUnitTest.exec")
                        include("outputs/code_coverage/debugAndroidTest/connected/*.ec")
                    }
                }

                sourceDirectories.setFrom(files(sourceDirs))
                classDirectories.setFrom(files(classDirs))
                executionData.setFrom(files(execData))

                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        } else {
            // サブプロジェクト（Androidモジュール）の場合、カバレッジ収集を有効化
            extensions.findByType<CommonExtension<*, *, *, *, *, *>>()?.apply {
                buildTypes {
                    getByName("debug") {
                        enableUnitTestCoverage = true
                        enableAndroidTestCoverage = true
                    }
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
}
