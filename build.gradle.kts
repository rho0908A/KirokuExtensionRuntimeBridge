plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * The CloudStream extension API is compiled with a newer Kotlin toolchain than this project.
 * Pin the Kotlin runtime and kotlinx libraries to the versions this project compiles against, and
 * skip metadata checks for that module (see backend-cloudstream/build.gradle.kts). The CloudStream
 * classes used here are JVM ABI stable; the plugin itself is loaded at runtime by the host.
 */
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}",
                // kotlin-reflect must match the stdlib exactly: a newer reflect implements interfaces
                // that only exist in its own stdlib, so a mismatched pair crashes at startup with
                // NoClassDefFoundError: kotlin/jvm/internal/KotlinGenericDeclaration.
                "org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${libs.versions.coroutines.get()}",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.coroutines.get()}",
                "org.jetbrains.kotlinx:kotlinx-serialization-core:${libs.versions.serialization.get()}",
                "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:${libs.versions.serialization.get()}",
                "org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}",
                "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:${libs.versions.serialization.get()}",
            )
        }
    }
}
