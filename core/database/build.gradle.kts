plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.chimali.core.database"
    compileSdk = 35
    defaultConfig { minSdk = 28 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqlcipher)
    
    // Hilt for dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
