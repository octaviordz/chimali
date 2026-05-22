pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Chimali"

include(":app")
include(":core:common")
include(":core:security")
include(":core:bluetooth")
include(":core:database")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":feature:vault")
include(":feature:editor")
include(":feature:authenticator")
include(":feature:fido2")
include(":feature:onboarding")
include(":feature:settings")
