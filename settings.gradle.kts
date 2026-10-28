pluginManagement {
    repositories {
        google()  // <-- Именно эта строка отвечает за поиск плагина Android
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

rootProject.name = "WifiRadar"
include(":app")
