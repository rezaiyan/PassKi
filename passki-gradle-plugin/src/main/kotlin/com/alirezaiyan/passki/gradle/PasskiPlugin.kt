package com.alirezaiyan.passki.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin `com.alirezaiyan.passki`.
 *
 * Registers the [PasskiExtension] (`passki { }`). Generation and verification tasks for
 * `assetlinks.json` and `apple-app-site-association` build on this extension.
 */
public class PasskiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("passki", PasskiExtension::class.java)
    }
}
