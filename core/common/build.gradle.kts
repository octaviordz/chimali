val isMac = System.getProperty("os.name").lowercase().contains("mac")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.chimali.core.common"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {}
        withDeviceTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.kermit)
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)

                api(libs.datastore.core.okio)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.platform.launcher)
                implementation(libs.mockk)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.kotlin.test)
            }
        }
        val iosMain by creating

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
    }
}

// KSP: Target-specific processor wiring for Koin Annotations (T189)
dependencies {
    add("kspAndroid", libs.koin.ksp.compiler)
    add("kspIosArm64", libs.koin.ksp.compiler)
    add("kspIosSimulatorArm64", libs.koin.ksp.compiler)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable native compilation on non-Mac hosts to support Windows development (T010)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    enabled = isMac
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    enabled = isMac
}
