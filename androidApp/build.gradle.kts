import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("com.alirezaiyan.passki")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.alirezaiyan.passki"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.alirezaiyan.passki"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        // Committed sample key so every checkout signs with the fingerprint the demo RP's assetlinks lists.
        // NOT for release: the password is public (see androidApp/README.md).
        getByName("debug") {
            storeFile = file("sample-debug.keystore")
            storePassword = "passki-sample"
            keyAlias = "passki-sample"
            keyPassword = "passki-sample"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

passki {
    rpId = "passki.alirezaiyan.com"
    server { configUrl = "https://passki.alirezaiyan.com/api/demo/server-config" }
    apple {
        teamId = "VFCFJC7Y5J"
        bundleId = "com.alirezaiyan.passki.PassKi"
        entitlementsFile = rootProject.file("iosApp/iosApp/iosApp.entitlements")
    }
}
