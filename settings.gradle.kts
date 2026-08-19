// Workaround for AGP error: Several environment variables and/or system properties contain different paths to the Android Preferences folder.
System.clearProperty("ANDROID_PREFS_ROOT")

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

rootProject.name = "DomoPi"
include(":app")
