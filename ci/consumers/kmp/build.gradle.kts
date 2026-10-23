// The oldest Kotlin PassKi supports: apps on it must be able to read its iOS, JS/Wasm and JVM libraries.
plugins { kotlin("multiplatform") version "2.2.21" }

val passkiVersion = gradle.extra["passkiVersion"] as String

kotlin {
    jvm()
    iosSimulatorArm64 { binaries.framework { baseName = "Consumer" } }
    sourceSets {
        commonMain.dependencies {
            implementation("com.alirezaiyan.passki:passki:$passkiVersion")
            implementation("com.alirezaiyan.passki:passki-demo:$passkiVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.alirezaiyan.passki:passki-test:$passkiVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
