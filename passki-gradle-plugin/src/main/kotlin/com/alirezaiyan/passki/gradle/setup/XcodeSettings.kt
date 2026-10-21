package com.alirezaiyan.passki.gradle.setup

import groovy.json.JsonSlurper
import java.io.File

/** Resolved build settings of the app target, from `xcodebuild -showBuildSettings -json`. */
internal data class XcodeSettings(val target: String?, val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    /** SRCROOT: the directory holding the .xcodeproj; relative build-setting paths resolve against it. */
    val projectDir: File? get() = (get("PROJECT_DIR") ?: get("SRCROOT"))?.let(::File)

    /** `CODE_SIGN_ENTITLEMENTS` resolved against [projectDir]; null when unset. */
    fun entitlementsFile(): File? {
        val value = get("CODE_SIGN_ENTITLEMENTS") ?: return null
        val file = File(value)
        return if (file.isAbsolute) file else File(projectDir ?: return file, value)
    }

    companion object {
        /** Picks the application target (WRAPPER_EXTENSION=app) from the JSON array; null when unparseable or empty. */
        fun parse(output: String): XcodeSettings? {
            val start = output.indexOf('[')
            if (start < 0) return null
            val entries = try {
                JsonSlurper().parseText(output.substring(start)) as? List<*>
            } catch (e: Exception) {
                null
            } ?: return null
            val parsed = entries.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val settings = (map["buildSettings"] as? Map<*, *>) ?: return@mapNotNull null
                XcodeSettings(map["target"]?.toString(), settings.entries.associate { it.key.toString() to it.value.toString() })
            }
            return parsed.firstOrNull { it["WRAPPER_EXTENSION"] == "app" || it["PRODUCT_TYPE"] == "com.apple.product-type.application" }
                ?: parsed.firstOrNull()
        }
    }
}
