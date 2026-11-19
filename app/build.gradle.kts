plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.chimali"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.chimali"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Default to the minified shrunkDebug target for instrumentation. Android test coverage
        // makes that target debuggable; choose release explicitly when validating release writers.
        // The clipboard audit uses a separate debuggable target for its isolated host procedure.
        testBuildType =
            providers.gradleProperty("chimaliTestBuildType").orNull
                ?: if (providers.gradleProperty("chimaliClipboardAudit").orNull == "true") {
                    "clipboardAudit"
                } else {
                    "shrunkDebug"
                }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("devRelease") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["devRelease"]
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            testProguardFiles("proguard-rules-test.pro")
        }
        create("shrunkDebug") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs["debug"]
            applicationIdSuffix = ".shrunk"
            isDebuggable = false
            enableAndroidTestCoverage = true
            proguardFiles("proguard-rules-leakcanary.pro")
            testProguardFiles("proguard-rules-test.pro")
        }
        create("clipboardAudit") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".clipboardaudit"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        create("clipboardAuditMinified") {
            initWith(getByName("clipboardAudit"))
            applicationIdSuffix = ".clipboardaudit.minified"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs["devRelease"]
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            testProguardFiles("proguard-rules-test.pro")
        }
        create("diagnosticRelease") {
            // Production Application graph with an isolated package identity for configured
            // writer instrumentation; this variant is never used as a product delivery build.
            initWith(getByName("release"))
            applicationIdSuffix = ".diagnosticrelease"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs["devRelease"]
            isDebuggable = false
            // Preserve only the direct Log.println instrumentation seam across R8 so the
            // release-derived sink probe can call it without introducing a test-only bridge.
            proguardFiles("proguard-rules-diagnostic-probe.pro")
        }
        create("diagnosticDebug") {
            // Debug Application graph with an isolated package identity for configured
            // writer instrumentation; never install over the user's normal app package.
            initWith(getByName("debug"))
            applicationIdSuffix = ".diagnosticdebug"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
        }
        create("heapAudit") {
            // Isolated debuggable package for controlled synthetic heap-retention experiments.
            // Never use this target with personal data or as a product delivery build.
            initWith(getByName("debug"))
            applicationIdSuffix = ".heapaudit"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Correct top-level block for Kotlin compiler options
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:bluetooth"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:authenticator"))
    implementation(project(":feature:fido2"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))

    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Koin (replaces Hilt)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)

    // BouncyCastle provider for global startup registration (US3)
    implementation(libs.bouncycastle.provider)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.tracing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)
    // shrunkDebug is the disposable instrumentation target variant, never the production release.
    add("shrunkDebugImplementation", libs.leakcanary.android)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
