plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.chimali.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 28 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    sourceSets.maybeCreate("main")
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sqldelight {
    databases {
        create("ChimaliDatabase") {
            packageName.set("com.chimali.core.database")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    api(libs.sqldelight.android)
    api(libs.sqldelight.coroutines)
    implementation(libs.sqlcipher)

    // Koin (replaces Hilt)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
