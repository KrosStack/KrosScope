package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginDescriptorStaticTest {
    private val tree = StaticWorkspace("class-conventions")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a plugin descriptor with a malformed escape is reported and the other plugins are still read`() {
        val descriptor = "build-logic/extras/src/main/resources/META-INF/gradle-plugins/unused.properties"
        tree.write(descriptor, "implementation-class=\\uZZZZ\n")
        val provided = tree.provided()
        val problem = provided.problems("gradle.static.plugin-descriptor-unreadable").single()
        assertEquals(DiagnosticSeverity.WARNING, problem.severity)
        assertTrue("unused.properties" in problem.message)
        assertTrue("acme.described" in provided.module(":plain").plugins)
    }
}
