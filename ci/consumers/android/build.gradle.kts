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
    // A minified release build with no PassKi keep rules in the app: whatever PassKi needs must ship in its
    // consumer rules. Debug signing, so CI needs no secrets.
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("com.alirezaiyan.passki:passki:$passkiVersion")
    implementation("com.alirezaiyan.passki:passki-demo:$passkiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0") // the app's own, as in most apps
}

passki {
    useDemoServer()
}
