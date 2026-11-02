package io.github.krosstack.kscope.analyzer.gradle.groovy

import kotlin.test.Test
import kotlin.test.assertEquals

class GroovyReceiverTest {
    private fun receiversOf(vararg lines: String) =
        GroovyScriptOutliner().outlineOf(lines.joinToString("\n"))?.calls?.map { it.receiver }

    @Test
    fun `a receiver written as a chain of properties is kept whole`() {
        assertEquals(
            listOf("project.extensions.extraProperties", "a.b.c", "rootProject.ext.foo", "foo.bar"),
            receiversOf(
                "project.extensions.extraProperties.set('k', 'v')",
                "a.b.c.d('x')",
                "rootProject.ext.foo.bar 'x'",
                "foo.bar.baz { x = 1 }",
            ),
        )
    }

    @Test
    fun `a receiver that starts with a call is kept whole`() {
        assertEquals(listOf("foo().bar", "a"), receiversOf("foo().bar.baz('x')", "a.b('x')"))
    }
}
