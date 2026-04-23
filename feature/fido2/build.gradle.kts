plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}


android {
    namespace = "com.chimali.fido2"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

sqldelight {
    databases {
        create("Fido2Database") {
            packageName.set("com.chimali.fido2.data.database")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
            version = 3
        }
    }
}

// Debug-only devtools: QR scanning + CameraX preview (Android variant only)
// These use debugImplementation which is unambiguous even in KMP library modules.
dependencies {
    add("debugImplementation", libs.compose.ui.tooling)
    add("debugImplementation", libs.compose.ui.test.manifest)
    add("debugImplementation", libs.qrose)
    add("debugImplementation", libs.camera.core)
    add("debugImplementation", libs.camera.camera2)
    add("debugImplementation", libs.camera.lifecycle)
    add("debugImplementation", libs.camera.view)
    add("debugImplementation", libs.mlkit.barcode.scanning)
}

kotlin {
    // Android target — all existing code lives in androidMain (src/main).
    // T190: Domain models, interfaces, and use cases will be moved to commonMain
    //        incrementally as KMP compatibility is verified.
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // iOS targets — placeholder, no actual implementations yet (T191/T192)
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        /**
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

        /**
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
            implementation(project(":core:security"))
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
            implementation(libs.sqlcipher)

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
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        // commonTest: runs on all targets — uses kotlin-test (not JUnit5 which is JVM-only)
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // androidUnitTest: JVM-hosted Android unit tests (JUnit5 + MockK + SQLDelight)
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.params)
                implementation(libs.junit.platform.launcher)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        // androidInstrumentedTest: on-device instrumented tests
        val androidInstrumentedTest by getting {
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
    add("androidMainImplementation", platform(libs.compose.bom))
}
