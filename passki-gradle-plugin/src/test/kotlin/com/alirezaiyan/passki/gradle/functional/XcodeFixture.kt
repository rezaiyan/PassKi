package com.alirezaiyan.passki.gradle.functional

import groovy.json.JsonOutput
import java.io.File

/** A KMP-template-shaped iosApp/ next to a TestKit project, plus a fake `xcodebuild` printing [settings]. */
internal class XcodeFixture(private val root: File) {
    val iosApp = File(root, "iosApp")
    val project = File(iosApp, "iosApp.xcodeproj")
    val xcconfig = File(iosApp, "Configuration/Config.xcconfig")
    val entitlements = File(iosApp, "iosApp/iosApp.entitlements")
    private val settingsJson = File(root, "fake-settings.json")
    val xcodebuild = File(root, "fake-xcodebuild")

    init {
        project.mkdirs()
        File(project, "project.pbxproj").writeText("// fake\n")
        xcconfig.parentFile.mkdirs()
        xcconfig.writeText("TEAM_ID=ABCDE12345\n\nPRODUCT_NAME=App\nPRODUCT_BUNDLE_IDENTIFIER=com.example.app\n")
        entitlements.parentFile.mkdirs()
        entitlements.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
            	<key>aps-environment</key>
            	<string>development</string>
            	<key>com.apple.developer.associated-domains</key>
            	<array>
            		<string>webcredentials:example.com?mode=developer</string>
            	</array>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )
        xcodebuild.writeText("#!/bin/sh\ncat '${settingsJson.absolutePath}'\n")
        xcodebuild.setExecutable(true)
        settings()
    }

    /** What the fake `xcodebuild -showBuildSettings -json` reports for the app target. */
    fun settings(extra: Map<String, String> = emptyMap()) {
        val values = linkedMapOf(
            "WRAPPER_EXTENSION" to "app",
            "DEVELOPMENT_TEAM" to "ABCDE12345",
            "PRODUCT_BUNDLE_IDENTIFIER" to "com.example.app",
            "PROJECT_DIR" to iosApp.absolutePath,
            "PROJECT_NAME" to "iosApp",
            "TARGET_NAME" to "iosApp",
            "FULL_PRODUCT_NAME" to "App.app",
        ) + extra
        settingsJson.writeText(JsonOutput.toJson(listOf(mapOf("action" to "build", "target" to "iosApp", "buildSettings" to values))))
    }

    /** A Simulator build in a fake DerivedData whose `-Simulated.xcent` lists [domains]; returns BUILD_DIR/OBJROOT settings. */
    fun simulatorBuild(vararg domains: String): Map<String, String> {
        val build = File(root, "DerivedData/Build")
        val app = File(build, "Products/Debug-iphonesimulator/App.app").apply { mkdirs() }
        val xcent = File(build, "Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/App.app-Simulated.xcent")
        xcent.parentFile.mkdirs()
        xcent.writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><plist version=\"1.0\"><dict>" +
                "<key>com.apple.developer.associated-domains</key><array>" +
                domains.joinToString("") { "<string>$it</string>" } + "</array></dict></plist>",
        )
        app.setLastModified(System.currentTimeMillis() + 60_000)
        return mapOf("BUILD_DIR" to File(build, "Products").absolutePath, "OBJROOT" to File(build, "Intermediates.noindex").absolutePath)
    }

    val xcodebuildArg: String get() = "-Ppasski.xcodebuild=${xcodebuild.absolutePath}"
}
