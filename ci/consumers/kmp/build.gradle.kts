// The oldest Kotlin PassKi supports: apps on it must be able to read its iOS, JS/Wasm and JVM libraries.
plugins { kotlin("multiplatform") version "2.2.21" }

val passkiVersion = gradle.extra["passkiVersion"] as String

kotlin {
    jvm()
    iosSimulatorArm64 { binaries.framework { baseName = "Consumer" } }
    sourceSets {
        // Versions come from the BOM, as the README's Install section shows.
        commonMain.dependencies {
            implementation(project.dependencies.platform("com.alirezaiyan.passki:passki-bom:$passkiVersion"))
            implementation("com.alirezaiyan.passki:passki")
            implementation("com.alirezaiyan.passki:passki-demo")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.alirezaiyan.passki:passki-test")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
