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
        // Pastikan baris ini ada jika Sceneform membutuhkan repo spesifik
        maven { url = uri("https://maven.google.com/ar") }
    }
}

rootProject.name = "ARPackageValidator"
include(":app")