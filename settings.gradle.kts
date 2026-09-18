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

rootProject.name = "Routina"

// Hub 本體。家族的子 App 各自住在自己的 repo（Flow、Bite…），
// 這裡只留 Hub 與它要用的共用層。
include(":hub")

// 共用層。contract 是「家族契約」的實作：掃描成員、讀能力、組 Intent。
include(":core:contract")
