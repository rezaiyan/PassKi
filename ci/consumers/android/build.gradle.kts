// A plain Android app: AGP's built-in Kotlin and an older compileSdk than PassKi's own.
plugins {
    id("com.android.application") version "9.1.1"
    id("com.alirezaiyan.passki")
}

val passkiVersion = gradle.extra["passkiVersion"] as String

android {
    namespace = "com.example.consumer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 28
    }
}

dependencies {
    implementation("com.alirezaiyan.passki:passki:$passkiVersion")
    implementation("com.alirezaiyan.passki:passki-demo:$passkiVersion")
}

passki {
    useDemoServer()
}
