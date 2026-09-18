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

// Hub 本體。家族的其他子 App 之後各自加一個 :apps:xxx module，
// 每個都是獨立的 application → 產出獨立 APK、獨立權限、獨立 release。
include(":hub")

// 共用層。contract 是「家族契約」的實作：掃描成員、讀能力、組 Intent。
include(":core:contract")
