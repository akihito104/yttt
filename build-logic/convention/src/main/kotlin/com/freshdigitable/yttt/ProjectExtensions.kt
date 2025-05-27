package com.freshdigitable.yttt

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.utils.IMPLEMENTATION

fun PluginManager.applyAndroidApplication() = apply("com.android.application")
fun PluginManager.applyAndroidLibrary() = apply("com.android.library")
fun PluginManager.applyKotlinAndroid() = apply("org.jetbrains.kotlin.android")
fun PluginManager.applyComposeCompiler() = apply("org.jetbrains.kotlin.plugin.compose")
fun PluginManager.applyKsp() = apply("com.google.devtools.ksp")
fun PluginManager.applyHiltAndroid() = apply("dagger.hilt.android.plugin")

fun DependencyHandlerScope.implementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add(IMPLEMENTATION, dependency)

fun DependencyHandlerScope.debugImplementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("debugImplementation", dependency)

fun DependencyHandlerScope.androidTestImplementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("androidTestImplementation", dependency)

fun DependencyHandlerScope.ksp(dependency: Provider<MinimalExternalModuleDependency>) =
    add("ksp", dependency)

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val VersionCatalog.androidxComposeBom: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("androidx-compose-bom").get()
val VersionCatalog.androidxComposeUiToolingPreview: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("androidx-compose-ui-tooling-preview").get()
val VersionCatalog.androidxComposeUiTooling: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("androidx-compose-ui-tooling").get()
val VersionCatalog.hiltAndroid: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("hilt-android").get()
val VersionCatalog.hiltCompiler: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("hilt-compiler").get()
