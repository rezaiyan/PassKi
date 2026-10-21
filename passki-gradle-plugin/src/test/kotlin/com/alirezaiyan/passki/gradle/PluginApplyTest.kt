package com.alirezaiyan.passki.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

internal class PluginApplyTest {
    @Test
    fun registersExtension() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.alirezaiyan.passki")
        assertNotNull(project.extensions.findByType(PasskiExtension::class.java))
    }
}
