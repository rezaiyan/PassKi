import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.util.Properties

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
}

group = "com.alirezaiyan.passki"
version = "0.1.0-alpha01"

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        // Must not exceed Gradle's embedded Kotlin (Gradle 9.5.1 → 2.3.20).
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("passki") {
            id = "com.alirezaiyan.passki"
            implementationClass = "com.alirezaiyan.passki.gradle.PasskiPlugin"
            displayName = "PassKi"
            description = "Generates and verifies assetlinks.json / apple-app-site-association for passkeys."
        }
    }
}

// Android SDK for TestKit functional tests: ANDROID_HOME, else sdk.dir from the root local.properties.
val androidSdkDir: String = providers.environmentVariable("ANDROID_HOME").orNull
    ?: rootDir.resolve("../local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("sdk.dir")
    }
    ?: (System.getProperty("user.home") + "/Library/Android/sdk")

tasks.test {
    // Functional tests build a real Android project.
    environment("ANDROID_HOME", androidSdkDir)
}
