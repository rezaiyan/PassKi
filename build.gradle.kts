plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.vanniktechPublish) apply false
    alias(libs.plugins.dokka)
}

dependencies {
    dokka(project(":passki"))
    dokka(project(":passki-test"))
    dokka(project(":passki-compose"))
}

dokka {
    moduleName.set("PassKi")
}
