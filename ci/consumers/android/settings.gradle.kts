import java.util.Properties

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // The plugin version under test: the root gradle.properties' VERSION_NAME.
        id("com.alirezaiyan.passki") version settings.rootDir.resolve("../../../gradle.properties").readLines()
            .first { it.startsWith("VERSION_NAME=") }.substringAfter("=").trim()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

// The PassKi version under test: the root gradle.properties' VERSION_NAME.
gradle.extra["passkiVersion"] = Properties().apply {
    rootDir.resolve("../../../gradle.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME")

rootProject.name = "passki-consumer-android"
