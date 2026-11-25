plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Binary compatibility layer for extension APKs.
 *
 * Neither Aniyomi nor Anikku publishes a usable `extensions-lib` artifact while extensions are built
 * with it as `compileOnly`, so the host has to provide these classes at runtime. This module
 * reproduces the API of the maintained hosts:
 *
 *  - anime: Anikku `eu.kanade.tachiyomi.animesource` (extensions-lib 12..16)
 *  - manga: Mihon  `eu.kanade.tachiyomi.source`      (extensions-lib 1.4..1.6)
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
