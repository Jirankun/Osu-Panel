// MIT License
// Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio

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

rootProject.name = "OsuPanelNative"
include(":app")
