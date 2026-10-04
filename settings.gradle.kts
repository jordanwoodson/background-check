pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven { url = uri("https://jitpack.io"); content { includeGroup("com.github.topjohnwu.libsu") } } }
}
rootProject.name = "BackgroundCheck"
include(":app")
include(":fixture")

include(":network")
