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
        // CloudStream publishes the extension API (`com.lagradost.cloudstream3`) only through JitPack,
        // and its AAR depends on JitPack-only libraries (NiceHttp, NewPipeExtractor).
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "KirokuExtensionRuntimeBridge"

// Shared IPC contract + unified models (used by both the SDK and the Runtime Host).
include(":bridge-ipc")

// The reusable library host applications depend on (produces an AAR).
include(":bridge-sdk")

// Backend abstraction shared by every extension ecosystem implementation.
include(":backend-api")

// Aniyomi API compatibility layer + backend implementation.
include(":aniyomi-api")
include(":backend-aniyomi")

// CloudStream backend implementation (API provided by the CloudStream library artifact).
include(":backend-cloudstream")

// The Runtime Host APK.
include(":runtime-host")

// Reference integration for host applications.
include(":sample-host")
