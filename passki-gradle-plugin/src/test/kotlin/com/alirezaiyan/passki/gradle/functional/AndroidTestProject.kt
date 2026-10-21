package com.alirezaiyan.passki.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume
import java.io.File
import java.util.concurrent.TimeUnit

/** A throwaway Android application project for TestKit functional tests. */
internal class AndroidTestProject(val dir: File) {
    val keystore: File = File(dir, "k.jks")

    init {
        Assume.assumeTrue("Android SDK not available (set ANDROID_HOME)", sdkDir != null)
        File(dir, "local.properties").writeText("sdk.dir=${sdkDir!!.absolutePath.replace("\\", "\\\\")}\n")
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "fixture"
            """.trimIndent(),
        )
        File(dir, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx1g\n")
    }

    /** Writes build.gradle.kts with an Android app, a release signing config and [passkiBlock]. */
    fun writeBuild(passkiBlock: String) {
        // AGP is on the plugin-under-test classpath (see build.gradle.kts), so no version here.
        File(dir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.alirezaiyan.passki")
            }

            android {
                namespace = "com.example.app"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = 24
                }
                signingConfigs {
                    create("release") {
                        storeFile = file("k.jks")
                        storePassword = "secret123"
                        keyAlias = "key0"
                        keyPassword = "secret123"
                    }
                }
                buildTypes {
                    release {
                        signingConfig = signingConfigs.getByName("release")
                    }
                }
            }

            $passkiBlock
            """.trimIndent(),
        )
    }

    fun generateKeystore() {
        keytool(
            "-genkeypair", "-keystore", keystore.absolutePath, "-storetype", "PKCS12",
            "-storepass", "secret123", "-keypass", "secret123", "-alias", "key0",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=PassKi Functional",
        )
    }

    fun runner(vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")
        .forwardOutput()

    fun build(vararg args: String): BuildResult = runner(*args).build()

    fun buildAndFail(vararg args: String): BuildResult = runner(*args).buildAndFail()

    companion object {
        val sdkDir: File? = System.getenv("ANDROID_HOME")?.let(::File)?.takeIf { it.isDirectory }

        private val keytoolBinary: String =
            File(System.getProperty("java.home"), "bin/keytool").takeIf { it.isFile }?.absolutePath ?: "keytool"

        fun keytool(vararg args: String): String {
            val process = ProcessBuilder(keytoolBinary, *args).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "keytool failed: $output" }
            return output
        }

        /** The AGP default debug keystore, when this machine has one. */
        val debugKeystore: File? =
            File(System.getProperty("user.home"), ".android/debug.keystore").takeIf { it.isFile }
    }
}
