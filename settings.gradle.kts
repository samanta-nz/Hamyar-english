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

rootProject.name = "HamyarEnglish"
include(":app")

includeBuild("third_party/kokoro-android") {
    dependencySubstitution {
        substitute(module("dev.ffmpegkit-maintained:kokoro-android"))
            .using(project(":library"))
    }
}
