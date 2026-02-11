pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
