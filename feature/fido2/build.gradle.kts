plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

private val androidMainImplementationName = "androidMainImplementation"

sqldelight {
    databases {
        create("Fido2Database") {
            packageName.set("com.chimali.fido2.data.database")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
        }
    }
}

// Debug-only devtools: QR scanning + CameraX preview (Android variant only)
// These use debugImplementation which is unambiguous even in KMP library modules.
dependencies {
    add(androidMainImplementationName, libs.compose.ui.tooling)
    add(androidMainImplementationName, libs.compose.ui.test.manifest)
    add(androidMainImplementationName, libs.qrose)
    add(androidMainImplementationName, libs.camera.core)
    add(androidMainImplementationName, libs.camera.camera2)
    add(androidMainImplementationName, libs.camera.lifecycle)
    add(androidMainImplementationName, libs.camera.view)
    add(androidMainImplementationName, libs.mlkit.barcode.scanning)
}

kotlin {
    android {
        namespace = "com.chimali.fido2"
        androidResources {
            enable = true
        }
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

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("proguard-rules.pro"))
        }
    }

    // iOS targets — placeholder, no actual implementations yet (T191/T192)
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        /*
         * commonMain: Platform-agnostic domain layer.
         *
         * Contains:
         *  - Domain models (PasskeyCredential, MakeCredentialOptions, etc.)
         *  - Repository interfaces
         *  - Use case definitions
         *  - Service interfaces (UserVerificationService, Fido2Service, etc.)
         *  - expect declarations for platform APIs
         *
         * T190 note: Files are progressively moved from androidMain to commonMain
         * as KMP compatibility is confirmed for each component.
         */
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:security"))
            implementation(libs.compose.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Signum — commonMain-safe cryptographic primitives
            implementation(libs.signum.indispensable)

            // Logging and IO
            implementation(libs.kermit)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)

            // Background Jobs
            implementation(libs.kmpworkmanager)
        }

        /*
         * androidMain: Android platform implementations.
         *
         * Contains all existing src/main code:
         *  - BluetoothHidDeviceWrapper (android.bluetooth.*)
         *  - UserVerificationServiceImpl (androidx.biometric.*)
         *  - all data/transport/crypto/di implementations
         *  - Compose UI screens
         */
        androidMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.security.crypto)
            implementation(libs.bouncycastle.provider)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.sqldelight.coroutines)
            implementation(project(":core:database"))

            // Compose UI (CMP — androidMain target)
            // Note: BOM applied via outer dependencies{} block (platform() deprecated in KMP sourceSets)
            implementation(project(":core:ui"))
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.animation.graphics)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.guava)
        }

        // iosMain: Placeholder — no functional code (T192). actual implementations added in T191+.
        getByName("iosArm64Main")
        getByName("iosSimulatorArm64Main")

        // commonTest: runs on all targets — uses kotlin-test (not JUnit5 which is JUnit5-only)
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // androidUnitTest: JVM-hosted Android unit tests (JUnit5 + MockK + SQLDelight)
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqlite.mc.android.unit.test)
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.params)
                implementation(libs.junit.platform.launcher)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.slf4j.simple)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        // androidDeviceTest: on-device instrumented tests
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.espresso.core)
                implementation(libs.compose.ui.test)
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.mockk.android)
            }
        }
    }
}

// KSP: Target-specific Koin Annotations processor (T189)
// KMP modules must use kspAndroid/kspIos* instead of the deprecated ksp()
dependencies {
    add("kspAndroid", libs.koin.ksp.compiler)
    add("kspIosArm64", libs.koin.ksp.compiler)
    add("kspIosSimulatorArm64", libs.koin.ksp.compiler)
    // Compose BOM: applied here (not inside KMP sourceSets) because
    // platform() inside KMP sourceSets{} is deprecated in Kotlin 2.3 (KT-58759)
    add(androidMainImplementationName, platform(libs.compose.bom))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

val isMac = System.getProperty("os.name").lowercase().contains("mac")

// Disable native compilation on non-Mac hosts to support Windows development (T010)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    enabled = isMac
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    enabled = isMac
}
