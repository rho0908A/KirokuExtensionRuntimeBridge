plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Binary compatibility layer for Aniyomi "lib 12..17" anime extensions.
 *
 * Aniyomi does not publish `extensions-lib` to a public Maven repository (the JitPack build only
 * produces an empty root jar), while extensions are compiled with those classes as `compileOnly`
 * and therefore expect the *host* to provide them at runtime. This module is that host-side API:
 * the same package names, types and method signatures the extensions were compiled against.
 *
 * Keep this module free of extension-specific logic: it is an API surface, not a backend.
 */
android {
    namespace = "eu.kanade.tachiyomi.animeextensions"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api("androidx.preference:preference-ktx:1.2.1")
    // Anime (Anikku) and manga (Mihon 1.4) extension APIs expose RxJava 1 observables.
    api("io.reactivex:rxjava:1.3.8")

    testImplementation(libs.junit)
}
