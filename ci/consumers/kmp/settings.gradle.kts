import java.util.Properties

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
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

rootProject.name = "passki-consumer-kmp"
