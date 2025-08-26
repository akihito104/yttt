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
import java.util.Optional

fun PluginManager.applyAndroidApplication() = apply("com.android.application")
fun PluginManager.applyAndroidLibrary() = apply("com.android.library")
fun PluginManager.applyKotlinAndroid() = apply("org.jetbrains.kotlin.android")
fun PluginManager.applyComposeCompiler() = apply("org.jetbrains.kotlin.plugin.compose")
fun PluginManager.applyKsp() = apply("com.google.devtools.ksp")
fun PluginManager.applyHiltAndroid() = apply("dagger.hilt.android.plugin")

fun DependencyHandlerScope.implementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add(IMPLEMENTATION, dependency)

fun DependencyHandlerScope.implementation(dependency: Optional<Provider<MinimalExternalModuleDependency>>) =
    add(IMPLEMENTATION, dependency.get())

fun DependencyHandlerScope.debugImplementation(dependency: Optional<Provider<MinimalExternalModuleDependency>>) =
    add("debugImplementation", dependency.get())

fun DependencyHandlerScope.testImplementation(dependency: Optional<Provider<MinimalExternalModuleDependency>>) =
    add("testImplementation", dependency.get())

fun DependencyHandlerScope.androidTestImplementation(dependency: Provider<MinimalExternalModuleDependency>) =
    add("androidTestImplementation", dependency)

fun DependencyHandlerScope.ksp(dependency: Optional<Provider<MinimalExternalModuleDependency>>) =
    add("ksp", dependency.get())

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val VersionCatalog.androidxComposeBom: Provider<MinimalExternalModuleDependency>
    get() = findLibrary("androidx-compose-bom").get()
val VersionCatalog.androidxComposeUiToolingPreview: Optional<Provider<MinimalExternalModuleDependency>>
    get() = findLibrary("androidx-compose-ui-tooling-preview")
val VersionCatalog.androidxComposeUiTooling: Optional<Provider<MinimalExternalModuleDependency>>
    get() = findLibrary("androidx-compose-ui-tooling")
val VersionCatalog.hiltAndroid: Optional<Provider<MinimalExternalModuleDependency>>
    get() = findLibrary("hilt-android")
val VersionCatalog.hiltCompiler: Optional<Provider<MinimalExternalModuleDependency>>
    get() = findLibrary("hilt-compiler")
