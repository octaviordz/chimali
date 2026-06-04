import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
// Android & Kotlin Platform Plugins
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
// Compose Plugins
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
// Dependency Injection & Serialization Plugins
    alias(libs.plugins.koin.compiler)
// Must add the Serialization plugin to support type-safe Nav3 routing states
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "app.chimali.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
// AndroidX Lifecycle
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
// Jetpack & Multiplatform Compose Core
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
// Adaptive Layouts & Navigation
            implementation(libs.compose.adaptive)
            implementation(libs.compose.adaptive.layout)
            implementation(libs.compose.adaptive.navigation)
            implementation(libs.jetbrains.material3.adaptiveNavigation3)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.material3.adaptive.navigation.suite)
// Koin Dependency Injection
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
// KotlinX Extensions
            implementation(libs.kotlinx.serialization.core)
// Local Project Modules
            implementation(projects.core.data)
            implementation(projects.core.datastore)
            implementation(projects.core.domain)
            implementation(projects.core.model)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
