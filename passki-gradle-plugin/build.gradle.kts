import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.util.Properties

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.vanniktechPublish)
}

group = "com.alirezaiyan.passki"
version = "0.1.0-alpha02"

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        // Must not exceed Gradle's embedded Kotlin (Gradle 9.5.1 → 2.3.20).
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

// TestKit injects the plugin-under-test classpath into its own classloader; AGP resolved from the
// fixture's plugins {} block would live elsewhere and be invisible to our plugin. Put AGP on the
// injected classpath instead so both share one classloader (fixtures apply it without a version).
val functionalTestPluginClasspath: Configuration by configurations.creating

dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    functionalTestPluginClasspath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
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

mavenPublishing {
    configure(GradlePlugin(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    coordinates(artifactId = "passki-gradle-plugin")
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
    pom {
        name.set("PassKi Gradle plugin")
        description.set("Generates and verifies assetlinks.json / apple-app-site-association for passkeys.")
        url.set("https://github.com/rezaiyan/PassKi")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("alirezaiyan")
                name.set("Ali Rezaiyan")
                url.set("https://alirezaiyan.com")
            }
        }
        scm {
            url.set("https://github.com/rezaiyan/PassKi")
            connection.set("scm:git:git://github.com/rezaiyan/PassKi.git")
            developerConnection.set("scm:git:ssh://git@github.com/rezaiyan/PassKi.git")
        }
    }
}

// Android SDK for TestKit functional tests: ANDROID_HOME, else sdk.dir from the root local.properties.
val androidSdkDir: String = providers.environmentVariable("ANDROID_HOME").orNull
    ?: rootDir.resolve("../local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("sdk.dir")
    }
    ?: (System.getProperty("user.home") + "/Library/Android/sdk")

// The shared check catalog (single source: docs/checks/catalog.json) ships inside the plugin jar.
tasks.processResources {
    from(layout.projectDirectory.file("../docs/checks/catalog.json")) {
        into("com/alirezaiyan/passki/gradle")
    }
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalTestPluginClasspath)
}

tasks.test {
    // Functional tests build a real Android project.
    environment("ANDROID_HOME", androidSdkDir)
}
