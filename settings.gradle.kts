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
        
        // Repositorio para AARs locales (GDAL)
        flatDir {
            dirs("app/libs")
        }
    }
}

rootProject.name = "DasoMaps"
include(":app")
