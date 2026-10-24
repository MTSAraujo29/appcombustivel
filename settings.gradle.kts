pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "FuelCompare"

include(
    ":app",
    ":core:common",
    ":core:designsystem",
    ":core:network",
    ":core:database",
    ":domain",
    ":data",
    ":feature:onboarding",
    ":feature:home",
    ":feature:stationdetails",
    ":feature:comparison",
    ":feature:settings",
)
