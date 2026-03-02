plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.chimali.fido2"
    compileSdk = 34

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
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    buildFeatures {
        compose = true
    }

    sqldelight {
        databases {
            create("Fido2Database") {
                packageName.set("com.chimali.fido2.data.database")
            }
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    
    // FIDO2 & Crypto
    implementation(libs.bouncycastle.provider)
    implementation(libs.kotlinx.coroutines.android)
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Database
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqlcipher)
    
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.sqldelight:sqlite-driver:2.0.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.3")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.58")
    kspAndroidTest(libs.hilt.compiler)
    
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.3")
}
