plugins {
    // build.gradle.kts uses jvmToolchain(17): download a JDK 17 when the machine has none (fresh clones, CI images).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "passki-gradle-plugin"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
